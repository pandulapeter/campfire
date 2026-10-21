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

import com.pandulapeter.campfire.data.model.importing.ImportConflictResolution
import com.pandulapeter.campfire.data.model.importing.ImportEntryKey
import com.pandulapeter.campfire.data.model.importing.ImportPlan
import com.pandulapeter.campfire.data.model.importing.ImportResult
import com.pandulapeter.campfire.data.model.setlist.Setlist
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class SharedLinkChoicesTest {

    private val plan = ImportPlan(
        songs = listOf(
            song("riverlight.cho", ImportPlan.Status.CONFLICTING),
            song("mine.cho", ImportPlan.Status.CONFLICTING),
            song("new.cho", ImportPlan.Status.NEW),
        ),
        setlists = listOf(
            ImportPlan.SetlistEntry(
                fileName = "gig.setlist.json",
                setlist = Setlist(
                    fileName = "gig.setlist.json",
                    title = "Gig",
                    description = "",
                    date = LocalDate(2026, 1, 1),
                    isArchived = false,
                    entries = emptyList(),
                    size = 0L,
                ),
                status = ImportPlan.Status.CONFLICTING,
                sourceFileName = "gig.setlist.json",
            ),
        ),
    )

    @Test
    fun `only the taken names the link wrote before start on Replace, whatever their case`() {
        val choices = sharedLinkChoices(plan, recorded = setOf("songs/Riverlight.cho", "setlists/gig.setlist.json", "songs/new.cho"))
        assertEquals(ImportConflictResolution.REPLACE, choices.resolutionOf(ImportEntryKey(ImportEntryKey.Kind.SONG, 0)))
        assertEquals(ImportConflictResolution.KEEP_BOTH, choices.resolutionOf(ImportEntryKey(ImportEntryKey.Kind.SONG, 1)))
        assertEquals(ImportConflictResolution.REPLACE, choices.resolutionOf(ImportEntryKey(ImportEntryKey.Kind.SETLIST, 0)))
        assertEquals(2, choices.conflictResolutions.size)
    }

    @Test
    fun `a song recorded under the setlists' folder is not the song`() {
        val choices = sharedLinkChoices(plan, recorded = setOf("setlists/riverlight.cho"))
        assertEquals(ImportConflictResolution.KEEP_BOTH, choices.resolutionOf(ImportEntryKey(ImportEntryKey.Kind.SONG, 0)))
    }

    @Test
    fun `a numbered copy kept beside the library's is not remembered as the link's`() {
        val result = ImportResult(importedSongFileNames = listOf("riverlight.cho", "mine_2.cho", "new.cho"), importedSetlistFileNames = listOf("gig_2.setlist.json"))
        assertEquals(listOf("riverlight.cho", "new.cho"), sharedLinkSongFileNamesWrittenBy(plan, result))
        assertEquals(emptyList(), sharedLinkSetlistFileNamesWrittenBy(plan, result))
    }

    private fun song(fileName: String, status: ImportPlan.Status) = ImportPlan.SongEntry(
        fileName = fileName,
        text = "{title: $fileName}",
        status = status,
        sourceFileName = fileName,
    )
}
