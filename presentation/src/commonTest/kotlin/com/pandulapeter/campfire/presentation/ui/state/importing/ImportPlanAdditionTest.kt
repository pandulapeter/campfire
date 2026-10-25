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

import com.pandulapeter.campfire.data.model.importing.ImportEntryKey
import com.pandulapeter.campfire.data.model.importing.ImportPlan
import com.pandulapeter.campfire.data.model.setlist.Setlist
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ImportPlanAdditionTest {

    @Test
    fun `an added batch comes after every entry under review, which keep their places`() {
        val plan = plan(songs = listOf(song("a.cho"), song("b.cho")), sourceNames = listOf("first.zip"))
        val added = plan(songs = listOf(song("c.cho", origin = 0)), sourceNames = listOf("second.zip"))
        val merged = plan.withAdded(added)
        assertEquals(listOf("a.cho", "b.cho", "c.cho"), merged.songs.map { it.fileName })
        assertEquals(1, merged.songs[2].origin)
        assertEquals(listOf("first.zip", "second.zip"), merged.sourceNames)
    }

    @Test
    fun `the same batch added again changes nothing`() {
        val plan = plan(
            songs = listOf(song("a.cho"), song("b.cho", status = ImportPlan.Status.IDENTICAL)),
            setlists = listOf(setlist("set.setlist.json", "a.cho")),
            skipped = listOf("cover.jpg"),
            sourceNames = listOf("backup.zip"),
        )
        assertSame(plan, plan.withAdded(plan))
    }

    @Test
    fun `a song already under review under another file name is not listed again`() {
        val plan = plan(songs = listOf(song("a.cho", text = "{title: A}")))
        val merged = plan.withAdded(plan(songs = listOf(song("a.cho", text = "{title: A}", sourceFileName = "A (copy).cho"), song("b.cho"))))
        assertEquals(listOf("a.cho", "b.cho"), merged.songs.map { it.fileName })
    }

    @Test
    fun `a repeat within the added batch follows its first copy to its new place`() {
        val plan = plan(songs = listOf(song("a.cho")))
        val added = plan(songs = listOf(song("b.cho"), song("b.cho", status = ImportPlan.Status.IDENTICAL, repeatedEntryIndex = 0)))
        assertEquals(listOf(null, null, 1), plan.withAdded(added).songs.map { it.repeatedEntryIndex })
    }

    @Test
    fun `a song an added setlist names stays as an unlisted repeat of the entry it matches`() {
        val plan = plan(songs = listOf(song("a.cho", sourceFileName = "A.cho")))
        val added = plan(songs = listOf(song("a.cho", sourceFileName = "Song A.cho")), setlists = listOf(setlist("set.setlist.json", "Song A.cho")))
        val merged = plan.withAdded(added)
        assertEquals(2, merged.songs.size)
        assertEquals(0, merged.songs[1].repeatedEntryIndex)
        assertEquals(ImportPlan.Status.IDENTICAL, merged.songs[1].status)
        assertEquals(1, merged.setlists.size)
    }

    @Test
    fun `a name the review already asks about is asked about once, the added song going in numbered`() {
        val plan = plan(songs = listOf(song("a.cho", text = "{title: A}\none", status = ImportPlan.Status.CONFLICTING)))
        val merged = plan.withAdded(plan(songs = listOf(song("a.cho", text = "{title: A}\ntwo", status = ImportPlan.Status.CONFLICTING))))
        assertEquals(listOf(ImportPlan.Status.CONFLICTING, ImportPlan.Status.NEW), merged.songs.map { it.status })
    }

    @Test
    fun `a question about a library file the added batch brings back unchanged is no longer asked`() {
        val plan = plan(songs = listOf(song("a.cho", text = "{title: A}\nother", status = ImportPlan.Status.CONFLICTING)))
        val merged = plan.withAdded(plan(songs = listOf(song("a.cho", text = "{title: A}", status = ImportPlan.Status.IDENTICAL))))
        assertEquals(listOf(ImportPlan.Status.NEW, ImportPlan.Status.IDENTICAL), merged.songs.map { it.status })
        assertFalse(merged.isConflicting(ImportEntryKey(ImportEntryKey.Kind.SONG, 0)))
    }

    @Test
    fun `a setlist under review is not listed again, and a second question about a setlist name goes in numbered`() {
        val plan = plan(setlists = listOf(setlist("set.setlist.json", "a.cho", status = ImportPlan.Status.CONFLICTING)))
        val added = plan(
            setlists = listOf(
                setlist("set.setlist.json", "a.cho", status = ImportPlan.Status.CONFLICTING),
                setlist("set.setlist.json", "b.cho", status = ImportPlan.Status.CONFLICTING),
            ),
        )
        val merged = plan.withAdded(added)
        assertEquals(listOf(ImportPlan.Status.CONFLICTING, ImportPlan.Status.NEW), merged.setlists.map { it.status })
        assertTrue(merged.isConflicting(ImportEntryKey(ImportEntryKey.Kind.SETLIST, 0)))
    }

    @Test
    fun `only file names not skipped already are added to the skipped ones`() {
        val plan = plan(skipped = listOf("cover.jpg"))
        assertEquals(listOf("cover.jpg", "notes.pdf"), plan.withAdded(plan(skipped = listOf("cover.jpg", "notes.pdf"))).skippedFileNames)
        assertSame(plan, plan.withAdded(plan()))
    }

    private fun plan(
        songs: List<ImportPlan.SongEntry> = emptyList(),
        setlists: List<ImportPlan.SetlistEntry> = emptyList(),
        skipped: List<String> = emptyList(),
        sourceNames: List<String> = emptyList(),
    ) = ImportPlan(songs = songs, setlists = setlists, skippedFileNames = skipped, sourceNames = sourceNames)

    private fun song(
        fileName: String,
        text: String = "{title: $fileName}",
        status: ImportPlan.Status = ImportPlan.Status.NEW,
        sourceFileName: String = fileName,
        repeatedEntryIndex: Int? = null,
        origin: Int? = null,
    ) = ImportPlan.SongEntry(
        fileName = fileName,
        text = text,
        status = status,
        sourceFileName = sourceFileName,
        repeatedEntryIndex = repeatedEntryIndex,
        origin = origin,
    )

    private fun setlist(fileName: String, vararg songFileNames: String, status: ImportPlan.Status = ImportPlan.Status.NEW) = ImportPlan.SetlistEntry(
        fileName = fileName,
        setlist = Setlist(
            fileName = fileName,
            title = fileName,
            description = "",
            date = LocalDate(2026, 10, 25),
            isArchived = false,
            entries = songFileNames.map { Setlist.Entry(songFileName = it) },
            size = 0,
        ),
        status = status,
        sourceFileName = fileName,
    )
}
