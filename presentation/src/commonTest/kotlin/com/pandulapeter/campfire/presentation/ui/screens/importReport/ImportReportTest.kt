/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.importReport

import com.pandulapeter.campfire.data.model.importing.ImportChoices
import com.pandulapeter.campfire.data.model.importing.ImportPlan
import com.pandulapeter.campfire.data.model.importing.ImportResult
import com.pandulapeter.campfire.data.model.setlist.Setlist
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ImportReportTest {

    @Test
    fun `every stage before the result is an import the process would lose`() {
        assertTrue(ImportReport.Loading(sourceName = "songs.zip").isUnfinished)
        assertTrue(ImportReport.Failed(reason = ImportFailure.UNREADABLE, sourceName = null).isUnfinished)
        assertTrue(ImportReport.Review(plan = ImportPlan(), choices = ImportChoices(), isDemoLibrary = false).isUnfinished)
        assertTrue(ImportReport.Importing.isUnfinished)
    }

    @Test
    fun `a result or no report at all loses nothing with the process`() {
        assertFalse(ImportReport.Finished(ImportResult(importedSongFileNames = listOf("a.cho"))).isUnfinished)
        assertFalse(ImportReport.Finished(null).isUnfinished)
        assertFalse((null as ImportReport?).isUnfinished)
    }

    @Test
    fun `a shared link's review is named by the setlist or the one song it holds, not by its download`() {
        val setlist = Setlist(
            fileName = "night.setlist.json", title = "Campfire night", description = "", date = LocalDate(2026, 10, 21),
            isArchived = false, entries = emptyList(), size = 0,
        )
        val songs = listOf(song("a.cho", "Riverlight"), song("b.cho", "Wonderwall"))
        val withSetlist = ImportPlan(
            songs = songs,
            setlists = listOf(ImportPlan.SetlistEntry("night.setlist.json", setlist, ImportPlan.Status.NEW, "night.setlist.json")),
            sourceNames = listOf("abcdefghijklmnop.zip"),
        )
        assertEquals("Campfire night", review(withSetlist, sharedLinkTitle = "Old title").subtitleName)
        assertEquals("Riverlight", review(ImportPlan(songs = songs.take(1)), sharedLinkTitle = "Old title").subtitleName)
        assertEquals("Old title", review(ImportPlan(songs = songs), sharedLinkTitle = "Old title").subtitleName)
        assertEquals("abcdefghijklmnop.zip", review(withSetlist, sharedLinkTitle = null).subtitleName)
    }

    @Test
    fun `a review of several files counts them, the skipped ones included, rather than naming them`() {
        val plan = ImportPlan(songs = listOf(song("a.cho", "Riverlight")), skippedFileNames = listOf("cover.jpg"), sourceNames = listOf("a.cho", "cover.jpg"))
        assertNull(review(plan, sharedLinkTitle = null).subtitleName)
        assertEquals(2, review(plan, sharedLinkTitle = null).sourceCount)
        assertEquals("a.cho", review(plan.copy(sourceNames = listOf("a.cho")), sharedLinkTitle = null).subtitleName)
    }

    private fun review(plan: ImportPlan, sharedLinkTitle: String?) =
        ImportReport.Review(plan = plan, choices = ImportChoices(), isDemoLibrary = false, sharedLinkTitle = sharedLinkTitle)

    private fun song(fileName: String, title: String) =
        ImportPlan.SongEntry(fileName = fileName, text = "", status = ImportPlan.Status.NEW, sourceFileName = fileName, title = title)
}
