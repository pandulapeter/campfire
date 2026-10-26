/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state.importing

import com.pandulapeter.campfire.data.model.importing.ImportPlan
import com.pandulapeter.campfire.data.model.importing.ImportResult
import com.pandulapeter.campfire.data.model.importing.ImportedFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImportOutcomeTest {

    @Test
    fun `an import the user started is shown on the import screen while it is read`() {
        assertTrue(requestOf("a.cho", "b.cho", shouldOpenSong = false).isReportedWhileRead(areSongsEnabled = true))
    }

    @Test
    fun `a single song file is read behind no screen, wherever it came from`() {
        assertFalse(requestOf("a.cho", shouldOpenSong = true).isReportedWhileRead(areSongsEnabled = true))
        assertFalse(requestOf("a.cho", shouldOpenSong = false).isReportedWhileRead(areSongsEnabled = true))
    }

    @Test
    fun `an archive or a document is shown on the import screen while it is read`() {
        assertTrue(requestOf("library.zip", shouldOpenSong = true).isReportedWhileRead(areSongsEnabled = true))
        assertTrue(requestOf("sheet.pdf", shouldOpenSong = false).isReportedWhileRead(areSongsEnabled = true))
    }

    @Test
    fun `an import that asks for no review is not shown while it is read`() {
        assertFalse(requestOf("library.zip", shouldOpenSong = false, shouldReview = false).isReportedWhileRead(areSongsEnabled = true))
    }

    @Test
    fun `with the songs switched off a single song file is shown on the import screen, the demo library still not`() {
        assertTrue(requestOf("a.cho", shouldOpenSong = true).isReportedWhileRead(areSongsEnabled = false))
        assertFalse(requestOf("library.zip", shouldOpenSong = false, shouldReview = false).isReportedWhileRead(areSongsEnabled = false))
    }

    @Test
    fun `an import with everything written is clean, duplicates included`() {
        assertTrue(ImportResult(importedSongFileNames = listOf("a.cho"), duplicateFileNames = listOf("b.cho")).isClean)
    }

    @Test
    fun `an import that left a file out or failed is not clean`() {
        assertFalse(ImportResult(leftOutFileNames = listOf("a.cho")).isClean)
        assertFalse(ImportResult(skippedFileNames = listOf("a.txt")).isClean)
        assertFalse(ImportResult(oversizedFileNames = listOf("a.pdf")).isClean)
        assertFalse(ImportResult(unreadableDocumentFileNames = listOf("a.docx")).isClean)
        assertFalse(ImportResult(isFailed = true).isClean)
    }

    @Test
    fun `a plan counts every file it has something to say about`() {
        val plan = ImportPlan(
            skippedFileNames = listOf("a.txt"),
            oversizedFileNames = listOf("b.pdf", "c.pdf"),
            unreadableDocumentFileNames = listOf("d.docx"),
        )

        assertEquals(4, plan.entryCount)
    }

    @Test
    fun `a batch of one new song is previewed, a taken name included`() {
        assertTrue(ImportPlan(songs = listOf(entry(ImportPlan.Status.NEW))).isPreviewed)
        assertTrue(ImportPlan(songs = listOf(entry(ImportPlan.Status.CONFLICTING))).isPreviewed)
        assertNull(ImportPlan(songs = listOf(entry(ImportPlan.Status.CONFLICTING))).librarySong)
    }

    @Test
    fun `a batch of one song the library holds as it is is opened rather than previewed`() {
        val plan = ImportPlan(songs = listOf(entry(ImportPlan.Status.IDENTICAL)))

        assertFalse(plan.isPreviewed)
        assertEquals(plan.songs.single(), plan.librarySong)
    }

    @Test
    fun `a batch of one song with anything else in it is reviewed rather than previewed`() {
        assertFalse(ImportPlan(songs = listOf(entry(ImportPlan.Status.NEW), entry(ImportPlan.Status.NEW))).isPreviewed)
        assertFalse(ImportPlan(songs = listOf(entry(ImportPlan.Status.NEW)), skippedFileNames = listOf("a.txt")).isPreviewed)
        assertNull(ImportPlan(songs = listOf(entry(ImportPlan.Status.IDENTICAL)), unreadableDocumentFileNames = listOf("b.pdf")).librarySong)
    }

    private fun entry(status: ImportPlan.Status) = ImportPlan.SongEntry(fileName = "a.cho", text = "{title: A}", status = status, sourceFileName = "a.cho")

    private fun requestOf(vararg names: String, shouldOpenSong: Boolean, shouldReview: Boolean = true) = ImportRequest(
        source = ImportSource.Files(names.map { ImportedFile(name = it, bytes = ByteArray(0)) }),
        shouldAnnounceResult = true,
        shouldOpenSong = shouldOpenSong,
        shouldReview = shouldReview,
    )
}
