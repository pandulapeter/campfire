/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.source

import com.pandulapeter.campfire.data.model.share.SharedLinkKind
import com.pandulapeter.campfire.data.model.sync.SyncOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipInputStream
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.measureTime

/**
 * A shared link's first copy (`CreateShareLinkUseCase`, before any run) and every later one (`SharedCopySync`) both come
 * from sync's `SharedCopyBuilder`, the first by the library's listing and the later ones by the run's index; the two
 * lookups must find the same files, so both make the same bytes, and hold exactly what the user shared. Each seed
 * generates a library ([SharedCopyLibrary]), shares some of its items on one device ([SharedCopyApp]), and syncs:
 *
 * - the run uploads no copy again, which it would where its build differed from the share's by a byte;
 * - each copy holds exactly the entries [SharedCopyLibrary.expectedCopy] works out, each the library's text but for
 *   the lines of attachments left out, and nothing else;
 * - editing a song nothing shared names, then syncing, changes no copy.
 *
 * Odd seeds find library files only under their stored spelling, as Android and Linux do. The seeds come from
 * `campfire.sharedCopyParitySeeds` (`gradle.properties`): 200 in every build, 10,000 by hand when `SharedCopyBuilder` or
 * the lookups change (`-Pcampfire.sharedCopyParitySeeds=10000`).
 */
class SharedCopyParityTest {

    @Test
    fun `a shared copy is built alike by the share and by sync and holds only what was shared`() {
        val seeds = System.getProperty("campfire.sharedCopyParitySeeds")?.toIntOrNull() ?: DEFAULT_SEEDS
        val problems = ConcurrentLinkedQueue<String>()
        val next = AtomicInteger(1)
        val copies = AtomicInteger()
        val leftOutAttachments = AtomicInteger()
        val respelledSongs = AtomicInteger()
        val largeSeeds = Mutex()
        // Each seed is a device of its own, so several run at once: most of a seed's time is spent waiting for its files.
        val time = measureTime {
            runBlocking(Dispatchers.Default) {
                repeat(PARALLEL_SEEDS) {
                    launch {
                        while (problems.size < MAX_PROBLEMS) {
                            val seed = next.getAndIncrement().takeIf { it <= seeds } ?: break
                            val library = SharedCopyLibrary(seed)
                            val checked = suspend {
                                check(library) { expected ->
                                    copies.incrementAndGet()
                                    leftOutAttachments.addAndGet(expected.leftOutAttachments)
                                    respelledSongs.addAndGet(expected.respelledSongs)
                                }
                            }
                            // One seed of tens of megabytes at a time, its copies, folder, and builds all holding them.
                            problems += if (library.hasLargeAttachments) largeSeeds.withLock { checked() } else checked()
                        }
                    }
                }
            }
        }
        println(
            "Shared copies of $seeds seeds checked in $time: $copies copies, $leftOutAttachments attachments left out, " +
                "$respelledSongs songs a setlist spells differently",
        )
        assertTrue(problems.isEmpty(), problems.sorted().joinToString(separator = "\n"))
        assertTrue(leftOutAttachments.get() > 0 && respelledSongs.get() > 0, "The libraries left out no attachment, or spelt no song differently")
    }

    private suspend fun check(library: SharedCopyLibrary, onExpected: (SharedCopyLibrary.ExpectedCopy) -> Unit) = SharedCopyApp(
        library = library,
        isExactNames = library.seed % 2 == 1,
    ).use { app ->
        val problems = mutableListOf<String>()
        app.start()
        val shares = library.shares.associateBy { (kind, fileName) -> app.share(kind, fileName).share.shareId }
        val shared = app.provider.sharedFiles.mapValues { it.value.first }
        val uploads = app.provider.sharedUploads.toMap()
        shares.forEach { (shareId, item) ->
            val label = "Seed ${library.seed}, ${item.first} ${item.second}"
            val expected = library.expectedCopy(item.first, item.second).also(onExpected)
            problems += compare(label, copy = entriesOf(shared.getValue(shareId)), expected = expected.entries)
        }
        listOf("the first run after sharing", "a run after an unshared song was edited").forEachIndexed { index, run ->
            if (index == 1) app.editUnsharedSong()
            val outcome = app.synchronize()
            if (outcome !is SyncOutcome.Success) problems += "Seed ${library.seed}: $run ended in $outcome"
            shares.forEach { (shareId, item) ->
                if (app.provider.sharedUploads[shareId] != uploads[shareId]) {
                    problems += "Seed ${library.seed}: $run uploaded the copy of ${item.second} again"
                }
                if (!app.provider.sharedFiles.getValue(shareId).first.contentEquals(shared.getValue(shareId))) {
                    val after = entriesOf(app.provider.sharedFiles.getValue(shareId).first)
                    problems += "Seed ${library.seed}: $run changed the copy of ${item.second}: " +
                        compare("after", copy = after, expected = entriesOf(shared.getValue(shareId)))
                }
            }
        }
        problems
    }

    private fun compare(label: String, copy: Map<String, ByteArray>, expected: Map<String, ByteArray>) = buildList {
        (copy.keys - expected.keys).forEach { add("$label: holds $it, which it should not") }
        (expected.keys - copy.keys).forEach { add("$label: lacks $it") }
        (copy.keys intersect expected.keys).filterNot { copy.getValue(it).contentEquals(expected.getValue(it)) }.forEach { name ->
            add("$label: $name differs: ${copy.getValue(name).preview()} instead of ${expected.getValue(name).preview()}")
        }
    }

    private fun entriesOf(zip: ByteArray) = buildMap {
        ZipInputStream(ByteArrayInputStream(zip)).use { stream ->
            generateSequence { stream.nextEntry }.forEach { entry -> put(entry.name, stream.readBytes()) }
        }
    }

    private fun ByteArray.preview() =
        if (size > PREVIEW_SIZE) "$size bytes" else "\"" + decodeToString().replace("\r", "\\r").replace("\n", "\\n") + "\""

    private companion object {
        const val DEFAULT_SEEDS = 200
        const val MAX_PROBLEMS = 20

        /** How many seeds run at once: a few, since a seed with large attachments holds hundreds of megabytes. */
        const val PARALLEL_SEEDS = 4
        const val PREVIEW_SIZE = 400
    }
}
