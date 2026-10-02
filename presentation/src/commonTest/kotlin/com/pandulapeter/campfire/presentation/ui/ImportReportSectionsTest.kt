/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui

import com.pandulapeter.campfire.data.model.domain.ImportResult
import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.presentation.ui.screens.importReport.ImportReportSection.Kind
import com.pandulapeter.campfire.presentation.ui.screens.importReport.importReportSections
import com.pandulapeter.campfire.presentation.ui.screens.importReport.matching
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ImportReportSectionsTest {

    @Test
    fun problemsComeBeforeWhatArrivedAndEmptyGroupsAreLeftOut() {
        val sections = importReportSections(
            result = ImportResult(
                importedSongFileNames = listOf("a.cho"),
                importedSetlistFileNames = emptyList(),
                skippedFileNames = listOf("photo.png"),
                duplicateFileNames = listOf("b.cho"),
                isFailed = true,
                failedFileNames = listOf("c.txt"),
                unprocessedFileNames = listOf("d.txt"),
            ),
            songs = listOf(song("a"), song("b")),
            setlists = emptyList(),
        )
        assertEquals(listOf(Kind.FAILED, Kind.UNPROCESSED, Kind.SKIPPED, Kind.SONGS, Kind.DUPLICATES), sections.map { it.kind })
    }

    @Test
    fun songsAndSetlistsAreNamedAsTheLibraryNamesThem() {
        val sections = importReportSections(
            result = ImportResult(
                importedSongFileNames = listOf("a.cho", "gone.cho"),
                importedSetlistFileNames = listOf("set.setlist.json"),
                skippedFileNames = emptyList(),
                duplicateFileNames = emptyList(),
                convertedSongFileNames = listOf("a.cho"),
            ),
            songs = listOf(song("a", artist = "Artist")),
            setlists = listOf(
                Setlist(
                    fileName = "set.setlist.json",
                    title = "Summer",
                    description = "",
                    date = null,
                    isArchived = false,
                    entries = emptyList(),
                    size = 0L,
                ),
            ),
        )
        val (written, gone) = sections.first { it.kind == Kind.SONGS }.rows
        assertEquals("Title a", written.title)
        assertEquals("Artist", written.subtitle)
        assertTrue(written.isSong)
        assertTrue(written.isConverted)
        // A song something removed since cannot be opened, and is listed by the name the import gave it.
        assertEquals(null, gone.title)
        assertFalse(gone.isSong)
        assertEquals("Summer", sections.first { it.kind == Kind.SETLISTS }.rows.single().title)
    }

    @Test
    fun aFileListedTwiceKeepsTwoRowsWithKeysOfTheirOwn() {
        val rows = importReportSections(
            result = ImportResult(
                importedSongFileNames = emptyList(),
                importedSetlistFileNames = emptyList(),
                skippedFileNames = listOf("notes.png", "notes.png"),
                duplicateFileNames = emptyList(),
            ),
            songs = emptyList(),
            setlists = emptyList(),
        ).single().rows
        assertEquals(2, rows.size)
        assertEquals(2, rows.map { it.key }.toSet().size)
    }

    @Test
    fun aSearchKeepsTheRowsWhoseNameTitleOrArtistHoldsItAndDropsEmptiedSections() {
        val sections = importReportSections(
            result = ImportResult(
                importedSongFileNames = listOf("a.cho", "b.cho"),
                importedSetlistFileNames = emptyList(),
                skippedFileNames = listOf("photo.png"),
                duplicateFileNames = emptyList(),
            ),
            songs = listOf(song("a", artist = "Queen"), song("b")),
            setlists = emptyList(),
        )
        assertEquals(listOf("a.cho"), sections.matching("QUEEN").single().rows.map { it.fileName })
        assertEquals(listOf("photo.png"), sections.matching(" photo ").single().rows.map { it.fileName })
        assertEquals(sections, sections.matching("  "))
        assertTrue(sections.matching("nothing like it").isEmpty())
    }

    private fun song(name: String, artist: String = "") = Song(
        fileName = "$name.cho", title = "Title $name", artist = artist, key = null, transpose = 0,
        tags = emptyList(), languages = emptyList(), coverArtUrl = null, hasChords = false,
        canUpdateFileName = false, lastModified = 0L, size = 0L,
    )
}
