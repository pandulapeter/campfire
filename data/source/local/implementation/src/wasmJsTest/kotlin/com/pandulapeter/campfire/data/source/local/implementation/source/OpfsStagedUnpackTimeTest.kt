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

import com.pandulapeter.campfire.data.formats.zip.ZipEntry
import com.pandulapeter.campfire.data.formats.zip.ZipWriter
import com.pandulapeter.campfire.data.model.Logger
import com.pandulapeter.campfire.data.model.importing.ImportLimits
import com.pandulapeter.campfire.data.model.importing.IncomingContent
import com.pandulapeter.campfire.data.source.local.implementation.staging.ImportStaging
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.OpfsFileStorage
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.StorageDirectory
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource
import kotlin.uuid.Uuid

/**
 * The web's time for the largest count of entries step 16 of the streaming archive import plan names: a ZIP64 archive
 * of 70,000 entries staged in the origin private file system and unpacked by the wasm target in headless Chrome
 * (`wasmJsBrowserTest`), where every read of the staged file is a promise, within [BUDGET]: each local header read once
 * and the `File` snapshot reused, so the walks over it do not turn into minutes.
 */
class OpfsStagedUnpackTimeTest {

    private val storage = OpfsFileStorage(rootSegments = listOf("campfire-staged-unpack-${Uuid.random()}"))

    @AfterTest
    fun tearDown() = runTest {
        storage.listNames(StorageDirectory.IMPORTS).forEach { storage.delete(StorageDirectory.IMPORTS, it) }
    }

    @Test
    fun `a staged archive of 70000 entries is unpacked within the budget`() = runTest(timeout = 15.minutes) {
        val songCount = ENTRY_COUNT / 4
        val archive = ZipWriter.write(
            (0 until songCount).map { ZipEntry("Songs/song_$it.cho", "{title: Song $it}\n{image: src=p$it.png}\n[C]La $it\n".encodeToByteArray()) } +
                (0 until songCount).map { ZipEntry("Songs/p$it.png", picture(it)) } +
                (0 until ENTRY_COUNT - 2 * songCount).map { ZipEntry("__MACOSX/Songs/._$it", byteArrayOf(0, 5, 22, 7)) },
        )
        val staging = ImportStaging(storage, Logger { })
        val sink = staging.openArchive(staging.newRequest(), announcedSize = archive.size.toLong())
        var offset = 0
        while (offset < archive.size) {
            val length = minOf(SLICE_SIZE, archive.size - offset)
            sink.write(archive, offset, length)
            offset += length
        }
        val staged = sink.finish()
        val start = TimeSource.Monotonic.markNow()

        val unpacked = ArchiveLocalSourceImpl(logger = Logger { }, fileStorage = storage, importStaging = staging).unpack(archive = staged, maxSize = ImportLimits.MAX_IMPORT_SIZE)

        val elapsed = start.elapsedNow()
        println("Unpacked $ENTRY_COUNT staged entries in headless Chrome in $elapsed")
        assertEquals(songCount, unpacked.files.size)
        assertTrue(unpacked.files.all { file -> file.images.singleOrNull()?.content is IncomingContent.Staged })
        assertTrue(elapsed < BUDGET, "Unpacking $ENTRY_COUNT staged entries took $elapsed, past $BUDGET")
    }

    /** A PNG header the import reads as a picture, then bytes of its own. */
    private fun picture(seed: Int): ByteArray {
        fun u32(value: Int) = byteArrayOf((value ushr 24).toByte(), (value ushr 16).toByte(), (value ushr 8).toByte(), value.toByte())
        val header = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) + u32(13) + "IHDR".encodeToByteArray() +
            u32(64) + u32(48) + byteArrayOf(8, 2, 0, 0, 0) + u32(0)
        return header + u32(seed) + u32(seed * 31)
    }

    private companion object {
        const val ENTRY_COUNT = 70_000
        const val SLICE_SIZE = 1024 * 1024
        val BUDGET = 5.minutes
    }
}
