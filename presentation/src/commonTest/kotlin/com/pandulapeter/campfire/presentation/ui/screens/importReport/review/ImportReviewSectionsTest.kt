/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.importReport.review

import com.pandulapeter.campfire.presentation.ui.screens.importReport.finished.matching
import com.pandulapeter.campfire.presentation.ui.screens.importReport.finished.sections
import androidx.compose.ui.state.ToggleableState
import com.pandulapeter.campfire.data.model.importing.ImportChoices
import com.pandulapeter.campfire.data.model.importing.ImportConflictResolution
import com.pandulapeter.campfire.data.model.importing.ImportEntryKey
import com.pandulapeter.campfire.data.model.importing.ImportPlan
import com.pandulapeter.campfire.data.model.setlist.Setlist
import com.pandulapeter.campfire.data.model.song.Song
import com.pandulapeter.campfire.presentation.ui.screens.importReport.review.ImportReviewSection.Kind
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
    fun `a preview pages through the songs listed, in their order, the one already there included and no setlist`() {
        val sections = importReviewSections(PLAN, LIBRARY_SONGS, emptyList())

        assertEquals(listOf(2, 0, 1, 3), sections.previewSongIndices)
        assertEquals(listOf(1), sections.matchingReview("two") { it.lowercase() }.previewSongIndices)
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
    fun `the group's choice is what every row comes to`() {
        val rows = listOf(conflictRow(FIRST_TAKEN), conflictRow(SECOND_TAKEN))
        val replace = ImportConflictResolution.REPLACE

        assertEquals(ImportConflictResolution.KEEP_BOTH, groupConflictResolution(rows, ImportChoices()))
        assertNull(groupConflictResolution(rows, ImportChoices(conflictResolutions = mapOf(FIRST_TAKEN to replace))))
        assertEquals(replace, groupConflictResolution(rows, ImportChoices(conflictResolutions = mapOf(FIRST_TAKEN to replace, SECOND_TAKEN to replace))))
        assertEquals(replace, groupConflictResolution(rows.take(1), ImportChoices(conflictResolutions = mapOf(FIRST_TAKEN to replace))))
    }

    @Test
    fun `choosing for the whole group drops the rows' own choices`() {
        val choices = ImportChoices(conflictResolutions = mapOf(FIRST_TAKEN to ImportConflictResolution.REPLACE))
            .withGroupResolution(keys = listOf(FIRST_TAKEN, SECOND_TAKEN), isFiltered = false, resolution = ImportConflictResolution.KEEP_BOTH)

        assertEquals(ImportConflictResolution.KEEP_BOTH, choices.resolutionOf(FIRST_TAKEN))
        assertEquals(ImportConflictResolution.KEEP_BOTH, choices.resolutionOf(SECOND_TAKEN))
        assertTrue(choices.conflictResolutions.isEmpty())
    }

    @Test
    fun `choosing for a narrowed group sets only the matching rows`() {
        val choices = ImportChoices().withGroupResolution(keys = listOf(FIRST_TAKEN), isFiltered = true, resolution = ImportConflictResolution.REPLACE)

        assertEquals(ImportConflictResolution.REPLACE, choices.resolutionOf(FIRST_TAKEN))
        assertEquals(ImportConflictResolution.KEEP_BOTH, choices.resolutionOf(SECOND_TAKEN))
    }

    @Test
    fun `a plan of nothing but files already there has nothing to choose`() {
        assertTrue(PLAN.hasEntriesToChoose)
        assertFalse(ImportPlan(songs = listOf(PLAN.songs[3]), skippedFileNames = listOf("x.mp3")).hasEntriesToChoose)
    }

    private val fold: (String) -> String = { it.lowercase().filter(Char::isLetterOrDigit) }

    private companion object {
        val CONFLICT = ImportEntryKey(ImportEntryKey.Kind.SONG, 2)
        val FIRST_TAKEN = ImportEntryKey(ImportEntryKey.Kind.SONG, 0)
        val SECOND_TAKEN = ImportEntryKey(ImportEntryKey.Kind.SONG, 1)

        fun conflictRow(key: ImportEntryKey) = ImportReviewRow(key = key, rowKey = "song_${key.index}", fileName = "taken_${key.index}.cho")

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
