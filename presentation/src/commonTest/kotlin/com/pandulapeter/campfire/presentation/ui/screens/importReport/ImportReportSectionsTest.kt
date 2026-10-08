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

import com.pandulapeter.campfire.data.model.domain.ImportResult
import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.presentation.ui.screens.importReport.ImportReportSection.Kind
import kotlinx.datetime.LocalDate
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
                    date = LocalDate(2026, 1, 1),
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
    fun aDeletedOrRenamedLibraryFileIsFollowedAndWhatWasLeftOutIsNot() {
        val result = ImportResult(
            importedSongFileNames = listOf("a.cho", "deleted.cho"),
            importedSetlistFileNames = listOf("deleted.setlist.json"),
            skippedFileNames = listOf("deleted.cho"),
            duplicateFileNames = listOf("a.cho"),
            convertedSongFileNames = listOf("a.cho", "deleted.cho"),
            convertedSongToOpen = "a.cho",
        ).followingLibraryFileNames { fileName ->
            when {
                fileName.startsWith("deleted") -> null
                fileName == "a.cho" -> "b.cho"
                else -> fileName
            }
        }
        assertEquals(
            ImportResult(
                importedSongFileNames = listOf("b.cho"),
                skippedFileNames = listOf("deleted.cho"),
                duplicateFileNames = listOf("b.cho"),
                convertedSongFileNames = listOf("b.cho"),
                convertedSongToOpen = "b.cho",
            ),
            result,
        )
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
        assertEquals(listOf("a.cho"), sections.matching("QUEEN", fold).single().rows.map { it.fileName })
        assertEquals(listOf("photo.png"), sections.matching(" photo ", fold).single().rows.map { it.fileName })
        assertEquals(sections, sections.matching("  ", fold))
        assertTrue(sections.matching("nothing like it", fold).isEmpty())
    }

    @Test
    fun aSearchIgnoresSpacesAndPunctuationTheWayTheLibrarySearchesDo() {
        val sections = importReportSections(
            result = ImportResult(
                importedSongFileNames = listOf("a.cho", "b.cho"),
                importedSetlistFileNames = emptyList(),
                skippedFileNames = emptyList(),
                duplicateFileNames = emptyList(),
            ),
            songs = listOf(song("a", title = "Y.M.C.A."), song("b")),
            setlists = emptyList(),
        )
        assertEquals(listOf("Y.M.C.A."), sections.matching("ymca", fold).single().rows.map { it.title })
        assertEquals(listOf("Y.M.C.A."), sections.matching("y m c a", fold).single().rows.map { it.title })
        assertEquals(sections, sections.matching("...", fold))
    }

    /** A stand-in for the library's folding, which lives in the domain implementation this module cannot reach. */
    private val fold: (String) -> String = { it.lowercase().filter(Char::isLetterOrDigit) }

    private fun song(name: String, artist: String = "", title: String = "Title $name") = Song(
        fileName = "$name.cho", title = title, artist = artist, key = null, transpose = 0,
        tags = emptyList(), languages = emptyList(), coverArtUrl = null, hasChords = false,
        canUpdateFileName = false, lastModified = 0L, size = 0L,
    )
}
