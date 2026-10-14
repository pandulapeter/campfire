/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state

import com.pandulapeter.campfire.data.model.domain.ImportPlan
import com.pandulapeter.campfire.data.model.domain.ImportResult
import com.pandulapeter.campfire.data.model.domain.ImportedFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ImportOutcomeTest {

    @Test
    fun `an import the user started is reviewed`() {
        assertTrue(requestOf("a.cho", "b.cho", shouldOpenSong = false).isReviewed)
    }

    @Test
    fun `a single song file the system handed over is opened rather than reviewed`() {
        assertFalse(requestOf("a.cho", shouldOpenSong = true).isReviewed)
    }

    @Test
    fun `an archive the system handed over is still reviewed`() {
        assertTrue(requestOf("library.zip", shouldOpenSong = true).isReviewed)
    }

    @Test
    fun `an import that asks for no review is not reviewed`() {
        assertFalse(requestOf("library.zip", shouldOpenSong = false, shouldReview = false).isReviewed)
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

    private fun requestOf(vararg names: String, shouldOpenSong: Boolean, shouldReview: Boolean = true) = ImportController.ImportRequest(
        source = ImportSource.Files(names.map { ImportedFile(name = it, bytes = ByteArray(0)) }),
        shouldAnnounceResult = true,
        shouldOpenSong = shouldOpenSong,
        shouldReview = shouldReview,
    )
}
