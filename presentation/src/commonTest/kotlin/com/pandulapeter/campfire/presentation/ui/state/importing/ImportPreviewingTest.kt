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

import com.pandulapeter.campfire.data.model.importing.ImportChoices
import com.pandulapeter.campfire.data.model.importing.ImportConflictResolution
import com.pandulapeter.campfire.data.model.importing.ImportEntryKey
import com.pandulapeter.campfire.data.model.importing.ImportPlan
import com.pandulapeter.campfire.presentation.ui.navigation.ImportPreview
import com.pandulapeter.campfire.presentation.ui.screens.importReport.ImportReport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImportPreviewingTest {

    @Test
    fun `a preview over the review pages through the rows it was opened on, then what joined since`() {
        val plan = ImportPlan(songs = listOf(entry("a.cho"), entry("b.cho"), entry("c.cho"), entry("d.cho"), entry("e.cho", repeatedEntryIndex = 3)))
        val preview = ImportPreview(songIndices = listOf(1, 0), planSongCount = 2, isReviewPager = true)

        assertEquals(listOf(1, 0, 2, 3), importPreviewSongIndices(preview, plan))
    }

    @Test
    fun `a page names no library file, and every entry its own`() {
        assertFalse(importPreviewFileName(0).endsWith(".cho"))
        assertTrue(importPreviewFileName(0) != importPreviewFileName(1))
    }

    @Test
    fun `a preview's song is read from its text, titled by the file it came in where the text names no title`() {
        val song = importPreviewSongOf(entry("untitled.cho", text = "{key: G}\n{tempo: 96}\n[G]Hello", title = "Untitled"), importPreviewFileName(0))

        assertEquals("Untitled", song.title)
        assertEquals("G", song.key)
        assertEquals(96, song.tempo)
        assertTrue(song.hasChords)
        assertNull(song.coverArtUrl)
        assertEquals(importPreviewFileName(0), song.fileName)
    }

    @Test
    fun `a page's choice follows the choices its row reads`() {
        val key = ImportEntryKey(ImportEntryKey.Kind.SONG, 0)
        val left = ImportChoices(leftOut = setOf(key))
        val replaced = ImportChoices(conflictResolutions = mapOf(key to ImportConflictResolution.REPLACE))

        assertEquals(ImportPreviewPageChoice.AlreadyInLibrary, importPreviewPageChoiceOf(entry("a.cho", status = ImportPlan.Status.IDENTICAL), key, left))
        assertEquals(ImportPreviewPageChoice.New(isChosen = false), importPreviewPageChoiceOf(entry("a.cho"), key, left))
        assertEquals(
            ImportPreviewPageChoice.NameTaken(isChosen = true, resolution = ImportConflictResolution.REPLACE),
            importPreviewPageChoiceOf(entry("a.cho", status = ImportPlan.Status.CONFLICTING), key, replaced),
        )
    }

    @Test
    fun `the preview of a free name has one button, which Ctrl S presses`() {
        val actions = importPreviewActionsOf(review(entry("a.cho")), isReviewPager = false)

        assertEquals(ImportPreviewActions(ImportPreviewAction.IMPORT), actions)
        assertEquals(ImportPreviewAction.IMPORT, actions?.saveShortcutAction)
    }

    @Test
    fun `the preview of a taken name offers the copy first and Replace beside it`() {
        val actions = importPreviewActionsOf(review(entry("a.cho", status = ImportPlan.Status.CONFLICTING)), isReviewPager = false)

        assertEquals(ImportPreviewActions(ImportPreviewAction.IMPORT_AS_COPY, ImportPreviewAction.REPLACE), actions)
        assertEquals(ImportPreviewAction.IMPORT_AS_COPY, actions?.saveShortcutAction)
    }

    @Test
    fun `a shared link imported here before offers Replace first, which Ctrl S never presses`() {
        val actions = importPreviewActionsOf(review(entry("a.cho", status = ImportPlan.Status.CONFLICTING), sharedLinkUpdateCount = 1), isReviewPager = false)

        assertEquals(ImportPreviewActions(ImportPreviewAction.REPLACE, ImportPreviewAction.IMPORT_AS_COPY), actions)
        assertNull(actions?.saveShortcutAction)
    }

    @Test
    fun `a preview over the review has the review's Import, and none where nothing can be ticked`() {
        assertEquals(ImportPreviewActions(ImportPreviewAction.IMPORT_REVIEW), importPreviewActionsOf(review(entry("a.cho"), entry("b.cho")), isReviewPager = true))
        assertNull(importPreviewActionsOf(review(entry("a.cho", status = ImportPlan.Status.IDENTICAL)), isReviewPager = true))
    }

    private fun review(vararg entries: ImportPlan.SongEntry, sharedLinkUpdateCount: Int = 0) = ImportReport.Review(
        plan = ImportPlan(songs = entries.toList()),
        choices = ImportChoices(),
        isDemoLibrary = false,
        sharedLinkUpdateCount = sharedLinkUpdateCount,
        isSongPreview = entries.size == 1,
    )

    private fun entry(
        fileName: String,
        status: ImportPlan.Status = ImportPlan.Status.NEW,
        text: String = "{title: $fileName}",
        title: String = fileName,
        repeatedEntryIndex: Int? = null,
    ) = ImportPlan.SongEntry(
        fileName = fileName,
        text = text,
        status = status,
        sourceFileName = fileName,
        repeatedEntryIndex = repeatedEntryIndex,
        title = title,
    )
}
