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

import androidx.compose.ui.state.ToggleableState
import com.pandulapeter.campfire.data.model.domain.ImportChoices
import com.pandulapeter.campfire.data.model.domain.ImportConflictResolution
import com.pandulapeter.campfire.data.model.domain.ImportEntryKey
import com.pandulapeter.campfire.data.model.domain.ImportPlan
import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.presentation.ui.screens.importReport.ImportReviewSection.Kind
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImportReviewSectionsTest {

    @Test
    fun `the groups come in order, and a repeat is counted on the row it follows rather than listed`() {
        val sections = importReviewSections(PLAN, LIBRARY_SONGS, emptyList())

        assertEquals(listOf(Kind.CONFLICTS, Kind.SONGS, Kind.SETLISTS, Kind.DUPLICATES, Kind.SKIPPED), sections.map { it.kind })
        val songs = sections.first { it.kind == Kind.SONGS }.rows
        assertEquals(listOf("One", "Two"), songs.map { it.title })
        assertEquals(listOf(1, 0), songs.map { it.repeatCount })
        assertEquals(1, sections.first { it.kind == Kind.DUPLICATES }.rows.size)
    }

    @Test
    fun `a taken name names the library song it is about, and what is already there has no box`() {
        val sections = importReviewSections(PLAN, LIBRARY_SONGS, emptyList())

        val conflict = sections.first { it.kind == Kind.CONFLICTS }.rows.single()
        assertEquals(ImportEntryKey(ImportEntryKey.Kind.SONG, 2), conflict.key)
        assertEquals("taken.cho", conflict.libraryFileName)
        assertEquals("The one you have", conflict.libraryTitle)
        val duplicate = sections.first { it.kind == Kind.DUPLICATES }.rows.single()
        assertNull(duplicate.key)
        assertEquals("Library title", duplicate.title)
        assertTrue(sections.first { it.kind == Kind.SKIPPED }.rows.all { it.key == null })
    }

    @Test
    fun `a setlist says how many of its songs arrive with it`() {
        val row = importReviewSections(PLAN, LIBRARY_SONGS, emptyList()).first { it.kind == Kind.SETLISTS }.rows.single()

        assertEquals(2, row.setlistSongCount)
        assertEquals(1, row.setlistSongsInImport)
    }

    @Test
    fun `every row has a key of its own`() {
        val keys = importReviewSections(PLAN, LIBRARY_SONGS, emptyList()).flatMap { it.rows }.map { it.rowKey }

        assertEquals(keys.distinct(), keys)
    }

    @Test
    fun `with everything ticked the summary counts what is written and what is already there`() {
        assertEquals(
            ImportReviewSummary(
                songCount = 3,
                setlistCount = 1,
                replacedCount = 0,
                numberedCount = 1,
                alreadyThereCount = 1,
                leftOutCount = 0,
                skippedCount = 1,
                oversizedCount = 0,
                unreadableCount = 0,
            ),
            importReviewSummary(PLAN, ImportChoices()),
        )
        assertEquals(4, importReviewSummary(PLAN, ImportChoices()).importCount)
    }

    @Test
    fun `an unticked conflict is left out rather than numbered`() {
        val summary = importReviewSummary(PLAN, ImportChoices(leftOut = setOf(CONFLICT)))

        assertEquals(2, summary.songCount)
        assertEquals(0, summary.numberedCount)
        assertEquals(1, summary.leftOutCount)
    }

    @Test
    fun `a conflict replaced is counted as replaced and named by the confirmation`() {
        val choices = ImportChoices(conflictResolutions = mapOf(CONFLICT to ImportConflictResolution.REPLACE))

        assertEquals(1, importReviewSummary(PLAN, choices).replacedCount)
        assertEquals(1, replaceCount(PLAN, choices))
        assertEquals(0, replaceCount(PLAN, choices.copy(leftOut = setOf(CONFLICT))))
    }

    @Test
    fun `the setlist alone writes one file`() {
        val choices = ImportChoices(leftOut = (0..2).map { ImportEntryKey(ImportEntryKey.Kind.SONG, it) }.toSet())

        val summary = importReviewSummary(PLAN, choices)
        assertEquals(1, summary.importCount)
        assertEquals(3, summary.leftOutCount)
    }

    @Test
    fun `a group's box is ticked, empty, or in between by the rows it holds`() {
        val rows = importReviewSections(PLAN, LIBRARY_SONGS, emptyList()).first { it.kind == Kind.SONGS }.rows

        assertEquals(ToggleableState.On, groupBoxState(rows, ImportChoices()))
        assertEquals(ToggleableState.Indeterminate, groupBoxState(rows, ImportChoices(leftOut = setOf(rows.first().key!!))))
        assertEquals(ToggleableState.Off, groupBoxState(rows, ImportChoices(leftOut = rows.mapNotNull { it.key }.toSet())))
    }

    @Test
    fun `a search narrows a group, and its box speaks for the rows it shows`() {
        val sections = importReviewSections(PLAN, LIBRARY_SONGS, emptyList()).matchingReview("two", fold)

        val songs = sections.single { it.kind == Kind.SONGS }.rows
        assertEquals(listOf("Two"), songs.map { it.title })
        val leftOut = ImportChoices(leftOut = setOf(ImportEntryKey(ImportEntryKey.Kind.SONG, 0)))
        assertEquals(ToggleableState.On, groupBoxState(songs, leftOut))
    }

    @Test
    fun `a plan of nothing but files already there has nothing to choose`() {
        assertTrue(PLAN.hasEntriesToChoose)
        assertFalse(ImportPlan(songs = listOf(PLAN.songs[3]), skippedFileNames = listOf("x.mp3")).hasEntriesToChoose)
    }

    private val fold: (String) -> String = { it.lowercase().filter(Char::isLetterOrDigit) }

    private companion object {
        val CONFLICT = ImportEntryKey(ImportEntryKey.Kind.SONG, 2)

        val PLAN = ImportPlan(
            songs = listOf(
                song(fileName = "one.cho", title = "One", status = ImportPlan.Status.NEW),
                song(fileName = "two.cho", title = "Two", status = ImportPlan.Status.NEW),
                song(fileName = "taken.cho", title = "Taken", status = ImportPlan.Status.CONFLICTING),
                song(fileName = "there.cho", title = "There", status = ImportPlan.Status.IDENTICAL),
                song(fileName = "one.cho", title = "One", status = ImportPlan.Status.IDENTICAL, sourceFileName = "copy of one.cho", repeatedEntryIndex = 0),
            ),
            setlists = listOf(
                ImportPlan.SetlistEntry(
                    fileName = "gig.setlist.json",
                    setlist = Setlist(
                        fileName = "gig.setlist.json",
                        title = "Gig",
                        description = "",
                        date = LocalDate(2026, 10, 13),
                        isArchived = false,
                        entries = listOf(Setlist.Entry("one.cho"), Setlist.Entry("elsewhere.cho")),
                        size = 0,
                    ),
                    status = ImportPlan.Status.NEW,
                    sourceFileName = "gig.setlist.json",
                ),
            ),
            skippedFileNames = listOf("cover.jpg"),
        )

        val LIBRARY_SONGS = listOf(librarySong("taken.cho", "The one you have"), librarySong("there.cho", "Library title"))

        fun song(
            fileName: String,
            title: String,
            status: ImportPlan.Status,
            sourceFileName: String = fileName,
            repeatedEntryIndex: Int? = null,
        ) = ImportPlan.SongEntry(
            fileName = fileName,
            text = "{title: $title}",
            status = status,
            sourceFileName = sourceFileName,
            repeatedEntryIndex = repeatedEntryIndex,
            title = title,
        )

        fun librarySong(fileName: String, title: String) = Song(
            fileName = fileName, title = title, artist = "", key = null, transpose = 0,
            tags = emptyList(), languages = emptyList(), coverArtUrl = null, hasChords = false,
            canUpdateFileName = false, lastModified = 0L, size = 0L,
        )
    }
}
