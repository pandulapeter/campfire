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

import com.pandulapeter.campfire.data.formats.zip.ByteArrayZipSource
import com.pandulapeter.campfire.data.formats.zip.ZipException
import com.pandulapeter.campfire.data.formats.zip.ZipReader
import com.pandulapeter.campfire.data.model.importing.ImportLimits
import com.pandulapeter.campfire.data.model.library.LibraryFiles
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The caps only the streaming reader has, set by the streaming archive import plan's decisions: at most
 * [ImportLimits.MAX_ARCHIVE_ENTRIES] records across an archive and the archives nested in it, and at most
 * [ImportLimits.MAX_CENTRAL_DIRECTORY_SIZE] of central directory held at once (an archive's and the nested ones being
 * walked inside it). The in-memory reader holds such an archive's nested archives as it reads them, so where an archive
 * passes either, the streaming path may report a nested archive unread whose songs the in-memory path brought.
 */
internal object StreamingCaps {

    /** Whether [archive], with every archive nested in it down to the unpack's last level, passes either cap. */
    suspend fun isPassed(archive: ByteArray): Boolean {
        var records = 0L
        var heldDirectories = 0L
        suspend fun visit(bytes: ByteArray, depth: Int, held: Long) {
            val directory = try {
                ZipReader.open(ByteArrayZipSource(bytes), maxCentralDirectorySize = Long.MAX_VALUE, maxEntries = Int.MAX_VALUE)
            } catch (_: ZipException) {
                return
            }
            records += directory.recordCount
            heldDirectories = maxOf(heldDirectories, held + directory.centralDirectorySize)
            if (depth == ArchiveEntryPolicy.MAX_DEPTH) return
            val content = try {
                ZipReader.read(ByteArrayZipSource(bytes), maxTotalSize = Long.MAX_VALUE)
            } catch (_: ZipException) {
                return
            }
            content.entries.filter { LibraryFiles.isArchiveFileName(it.name) || LibraryFiles.isZipArchive(it.bytes) }
                .forEach { visit(it.bytes, depth + 1, held + directory.centralDirectorySize) }
        }
        visit(archive, depth = 0, held = 0)
        return records > ImportLimits.MAX_ARCHIVE_ENTRIES || heldDirectories > ImportLimits.MAX_CENTRAL_DIRECTORY_SIZE
    }

    /**
     * Checks that the streaming path's [new] outcome differs from the in-memory [old] one only by nested archives it
     * reported unread: those are the only names it skipped that the other did not, and everything it brought (songs,
     * setlists, library files) the other brought alike.
     */
    fun check(label: String, old: ImportOutcome, new: ImportOutcome) {
        val oldPlan = assertNotNull(old.plan, "$label: the in-memory path failed: ${old.failure}")
        val newPlan = assertNotNull(new.plan, "$label: the streaming path failed: ${new.failure}")
        val unread = newPlan.skippedFileNames.toMutableList().apply { oldPlan.skippedFileNames.forEach { remove(it) } }
        assertTrue(unread.isNotEmpty() && unread.all(LibraryFiles::isArchiveFileName), "$label: the streaming path skipped $unread, not nested archives")
        assertEquals(oldPlan.copy(songs = emptyList(), setlists = emptyList(), skippedFileNames = emptyList()), newPlan.copy(songs = emptyList(), setlists = emptyList(), skippedFileNames = emptyList()), label)
        val oldSongs = oldPlan.songs.map { it.fileName to it.text }
        newPlan.songs.forEach { assertTrue(it.fileName to it.text in oldSongs, "$label: ${it.fileName} came only on the streaming path") }
        newPlan.setlists.forEach { setlist -> assertTrue(oldPlan.setlists.any { it.setlist == setlist.setlist }, "$label: ${setlist.fileName} came only on the streaming path") }
        new.library.forEach { (path, hash) -> assertEquals(hash, old.library[path], "$label: $path") }
    }
}
