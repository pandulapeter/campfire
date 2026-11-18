/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.sync.implementation

import com.pandulapeter.campfire.chordpro.edit.libraryFilesNamedBy
import com.pandulapeter.campfire.data.model.library.LibraryFileKind
import com.pandulapeter.campfire.data.model.library.LibraryFiles
import com.pandulapeter.campfire.data.model.share.CreateShareResult
import com.pandulapeter.campfire.data.model.share.SharedLinkKind
import com.pandulapeter.campfire.data.source.remote.api.hashing.localContentHash
import com.pandulapeter.campfire.data.sync.implementation.shared.SharedLinksEntry
import com.pandulapeter.campfire.data.sync.implementation.shared.registry
import kotlinx.coroutines.flow.first
import kotlin.random.Random

/**
 * The shared links of [SyncSimulation]'s library: items shared from any device, stopped one at a time or all at once on
 * any device, also while another device's run is uploading their copies, and every rename and deletion a user makes
 * noted the way the app's use cases note them ([Device.sharing]). What it checks:
 * - a share stops (its copy or its entry gone) only where a user stopped it, or deleted the item under a name it had;
 * - a share is marked `copyMissing` only where its copy is truly gone from the folder;
 * - once a stop answers that it stopped a share and the list no longer names it, no run started after that uploads its
 *   copy again (unless that run's listing was falsified, which may name the copy still), no later share of the item is
 *   answered with its id, and no copy of it is in the folder, except the accepted leftover: one a run that read the list before the stop uploaded,
 *   while that run is under way, or after it ended short of its final edit or met a fault in between, since deleting
 *   such a copy again is best effort (`shared/CLAUDE.md`), until Stop sharing everything deletes it. A stop the list
 *   could not be told of at once (it failed, and the stop waits in the journal) promises none of this: the share's other
 *   devices keep its copy current until the journal is applied, or for good where a disconnect forgets it (an accepted
 *   edge, which the Disconnect confirmation names), a dead link either way;
 * - settled, every share whose copy is there holds exactly its item, the songs a setlist names that the folder holds,
 *   and the files those songs name (each the folder's bytes), compared as whole sets, or is orphaned where the folder
 *   has no such item.
 */
internal class SimulatedSharing(private val cloud: FakeSyncProvider, private val random: Random) {

    /** The items a user deleted, by kind and folded name, which is what may stop a share of them. */
    private val deletedNames = mutableSetOf<String>()

    /** Every name each share was seen under, by its id. */
    private val namesOf = mutableMapOf<String, MutableSet<String>>()

    /** The shares a user pressed a stop for, whatever it answered, which is what else may stop them. */
    private val stoppedByUser = mutableSetOf<String>()

    /** The shares a stop is under way for, and those a stop answered it stopped, with how many stops had answered then. */
    private val stopping = mutableSetOf<String>()
    private val stopped = mutableMapOf<String, Int>()
    private var stopsAnswered = 0
    private var isStoppingEverything = false

    /** Each run under way or last made: when it started, counted in stops answered, and whether its listing was falsified. */
    private val runs = mutableMapOf<Device, Run>()

    /** The copies uploaded under a share being stopped or stopped, by its id: the leftovers that may stay. */
    private val leftovers = mutableMapOf<String, Run>()

    private class Run(val startedAfter: Int, val mayListStale: Boolean) {
        var isUnderWay = true
        var isClean = true
    }

    suspend fun share(device: Device, key: SyncKey): String {
        val bytes = device.library.files.getValue(key)
        val result = device.sharing(cloud).share(
            kind = key.kind.shareKind,
            fileName = key.name,
            title = bytes.decodeToString().lines().first(),
            copy = device.sharedContents.pack(mapOf(key.name to bytes)),
            leftOutAttachmentCount = 0,
        )
        observe()
        if (result !is CreateShareResult.Shared) return "not shared: $result"
        verify(result.share.shareId !in stopped) { "Sharing ${key.name} again answered the stopped share ${result.share.shareId}" }
        return "shared as ${result.share.shareId}"
    }

    /** Stop sharing, pressed on [device] for one of the shares it shows. */
    suspend fun stop(device: Device): String {
        val shareId = device.sharing(cloud).shares.first().randomOrNull(random)?.shareId ?: return "nothing to stop"
        stoppedByUser += shareId
        stopping += shareId
        val failure = device.sharing(cloud).stopSharing(shareId)
        stopping -= shareId
        if (failure == null && isUnlisted(shareId)) stopped[shareId] = ++stopsAnswered
        observe()
        return "stops sharing $shareId: ${failure ?: "stopped"}"
    }

    /** Stop sharing everything, pressed on [device]: every share and copy there is now. */
    suspend fun stopEverything(device: Device): String {
        val shareIds = cloud.registry().links.map { it.shareId } + cloud.sharedFiles.keys
        stoppedByUser += shareIds
        stopping += shareIds
        // What it meets is deleted whatever was uploaded before, a leftover included.
        shareIds.forEach(leftovers::remove)
        isStoppingEverything = true
        val failure = try {
            device.sharing(cloud).stopSharingEverythingNow()
        } finally {
            isStoppingEverything = false
        }
        stopping -= shareIds.toSet()
        if (failure == null) {
            stopsAnswered++
            shareIds.filter(::isUnlisted).forEach { stopped[it] = stopsAnswered }
        }
        observe()
        return "stops sharing everything: ${failure ?: "stopped"}"
    }

    private fun isUnlisted(shareId: String) = cloud.registry().links.none { it.shareId == shareId }

    /** Whether a stop noted for [device]'s next run waits in its journal, which a disconnect would forget. */
    suspend fun hasPendingStops(device: Device) = device.sharing(cloud).hasPendingStops.first()

    fun onRunStarted(device: Device, mayListStale: Boolean) {
        runs[device] = Run(startedAfter = stopsAnswered, mayListStale = mayListStale)
    }

    /** A fault struck: a deletion it failed may be the one that would have taken a leftover away. */
    fun onFault() = runs.values.filter { it.isUnderWay }.forEach { it.isClean = false }

    fun onRunEnded(device: Device, isClean: Boolean) {
        val run = runs[device] ?: return
        run.isUnderWay = false
        if (!isClean) run.isClean = false
    }

    /** [device]'s run, or a sharing action outside one where null, wrote the copy [shareId]. */
    fun onCopyWritten(shareId: String, device: Device?) {
        // A share made while Stop sharing everything is under way, which that stop may meet, as the app's may.
        if (isStoppingEverything && shareId !in namesOf && shareId !in stopping) stoppedByUser += shareId
        if (shareId !in stopping && shareId !in stopped) return
        val run = device?.let(runs::get)?.takeIf { it.isUnderWay }
            ?: throw AssertionError("The copy of the stopped share $shareId was uploaded again outside any run")
        stopped[shareId]?.let { stoppedAt ->
            verify(run.startedAfter < stoppedAt || run.mayListStale) { "A run started after $shareId stopped uploaded its copy again" }
        }
        leftovers[shareId] = run
    }

    /** Records the names the list gives its shares now, and checks that none stopped without a user stopping it. */
    fun observe() {
        val links = cloud.registry().links
        links.forEach { namesOf.getOrPut(it.shareId) { mutableSetOf() } += it.folded() }
        namesOf.forEach { (shareId, names) ->
            val isListed = links.any { it.shareId == shareId }
            val isCopied = shareId in cloud.sharedFiles
            verify((isListed && isCopied) || shareId in stoppedByUser || names.any { it in deletedNames }) {
                "Share $shareId of ${names.joinToString()} stopped (listed: $isListed, copy: $isCopied) though nobody stopped it or deleted its item"
            }
        }
        links.filter { it.copyMissing }.forEach { entry ->
            verify(entry.shareId !in cloud.sharedFiles) { "Share ${entry.shareId} of ${entry.fileName} is marked dead while its copy is there" }
        }
        stopped.keys.filter { it in cloud.sharedFiles && it !in stopping }.forEach { shareId ->
            val leftover = leftovers[shareId] ?: throw AssertionError("The stopped share $shareId still has its copy")
            verify(leftover.isUnderWay || !leftover.isClean) { "The stopped share $shareId has its copy back after the run that uploaded it ended cleanly" }
        }
    }

    suspend fun noteDeletion(device: Device, key: SyncKey) {
        deletedNames += key.folded()
        device.sharing(cloud).stopSharingDeleted(key.kind.shareKind, key.name)
    }

    suspend fun noteRename(device: Device, from: SyncKey, to: SyncKey) = device.sharing(cloud).followRename(from.kind.shareKind, from.name, to.name)

    /**
     * Every share follows the library the folder settled on, its copy holding exactly what it shares. Every file is far
     * within the budgets, so the files the songs name all belong in the copy.
     */
    fun checkSettled() {
        val folder = cloud.files.filter { (key, file) -> key.kind != LibraryFileKind.FILE || isIntact(key, file.first) }
            .entries.associate { (key, file) -> key.folded() to (key to file.first.decodeToString()) }
        cloud.registry().links.filterNot { it.copyMissing }.forEach { entry ->
            val item = folder[entry.folded()]?.second
            if (item == null) {
                verify(entry.state == SharedLinksEntry.STATE_ORPHANED) { "Share ${entry.shareId} of the missing ${entry.fileName} is ${entry.state}" }
                return@forEach
            }
            verify(entry.state == SharedLinksEntry.STATE_OK) { "Share ${entry.shareId} of ${entry.fileName} is ${entry.state}" }
            // The item under the list's spelling, a setlist's songs under its own, and the files under `images/`.
            val songs = if (entry.kind == SharedLinkKind.SETLIST.id) {
                item.lines().drop(1).filter { it.isNotBlank() }.distinct().mapNotNull { name -> folder[songKey(name).folded()]?.let { name to it.second } }
            } else {
                emptyList()
            }
            val texts = if (entry.kind == SharedLinkKind.SETLIST.id) songs.map { it.second } else listOf(item)
            val named = texts.flatMapTo(sortedSetOf()) { libraryFilesNamedBy(it) }
            val files = named.mapNotNull { name -> folder["${LibraryFileKind.FILE.name}/$name"]?.let { IMAGES_FOLDER + name to it.second } }
            val expected = (listOf(entry.fileName to item) + songs + files).toMap()
            // A file whose folder copy is damaged past repair (`run/CLAUDE.md`) no device holds, so a copy built since
            // lacks it, while one built before keeps the intact file.
            val copy = entriesOf(cloud.sharedFiles.getValue(entry.shareId).first.decodeToString()).filterNot { (name, text) ->
                val file = name.removePrefix(IMAGES_FOLDER)
                name.startsWith(IMAGES_FOLDER) && IMAGES_FOLDER + file !in expected && file in named &&
                    isIntact(SyncKey(kind = LibraryFileKind.FILE, name = file), text.encodeToByteArray())
            }
            verify(copy == expected) { "Share ${entry.shareId} of ${entry.fileName} holds ${copy.keys} rather than ${expected.keys}, or other bytes" }
        }
    }

    /** The list and the copies, for telling whether a round of runs moved anything. */
    fun state() = cloud.registry().links.map { "${it.shareId}=${it.fileName}/${it.state}/${it.copyMissing}" } +
        cloud.sharedFiles.entries.map { (shareId, file) -> "$shareId@${file.second}" }

    fun describe() = "Shares: " + cloud.registry().links.joinToString { entry ->
        "${entry.shareId}=${entry.fileName} (${entry.state}${if (entry.copyMissing) ", copy missing" else ""}): " +
            cloud.sharedFiles[entry.shareId]?.first?.decodeToString()?.substringAfter('\n')
    } + "\nCopies: " + cloud.sharedFiles.keys.sorted().joinToString()

    /**
     * Fails the simulation even where it is checked inside a request the app's code makes, whose failures that code
     * catches as any `Exception`.
     */
    private fun verify(value: Boolean, message: () -> String) {
        if (!value) throw AssertionError(message())
    }

    /** The entries of a copy as `FakeSharedCopyContents` packs them: a `[name]` line, then the file's text. */
    private fun entriesOf(copy: String): Map<String, String> {
        val entries = mutableMapOf<String, MutableList<String>>()
        var current: MutableList<String>? = null
        copy.lines().forEach { line ->
            val name = ENTRY.matchEntire(line)?.groupValues?.get(1)
            if (name != null) {
                current = mutableListOf<String>().also { entries[name] = it }
            } else {
                checkNotNull(current) { "A copy starts with no entry: $copy" } += line
            }
        }
        return entries.mapValues { it.value.joinToString("\n") }
    }

    private fun songKey(name: String) = SyncKey(kind = LibraryFileKind.SONG, name = name)

    private fun isIntact(key: SyncKey, bytes: ByteArray) = LibraryFiles.isLibraryFileNamedFor(key.name, localContentHash(bytes))

    private val LibraryFileKind.shareKind get() = if (this == LibraryFileKind.SONG) SharedLinkKind.SONG else SharedLinkKind.SETLIST

    private fun SyncKey.folded() = "${kind.name}/${LibraryFiles.identityKey(name)}"

    private fun SharedLinksEntry.folded() = "${if (kind == SharedLinkKind.SONG.id) LibraryFileKind.SONG.name else LibraryFileKind.SETLIST.name}/${LibraryFiles.identityKey(fileName)}"

    private companion object {
        const val IMAGES_FOLDER = "images/"
        val ENTRY = Regex("""\[(.+)]""")
    }
}
