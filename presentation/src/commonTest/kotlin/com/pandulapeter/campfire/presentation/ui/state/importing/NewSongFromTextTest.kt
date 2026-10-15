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

import com.pandulapeter.campfire.chordpro.edit.ChordProMetadataFields.Field
import com.pandulapeter.campfire.data.model.importing.ImportPlan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NewSongFromTextTest {

    private fun song(
        text: String,
        status: ImportPlan.Status = ImportPlan.Status.NEW,
        sourceFileName: String? = "First line.txt",
        isConverted: Boolean = true,
    ) = ImportPlan.SongEntry(
        fileName = "song.cho",
        text = text,
        status = status,
        sourceFileName = sourceFileName,
        isConverted = isConverted,
    )

    @Test
    fun `a song whose name is taken is a form filled in with what it says`() {
        val text = "{title: Song}\n{subtitle: Live}\n{artist: Band}\n[G]Hello\n"
        val dialog = assertNotNull(newSongFromTextDialogOf(ImportPlan(songs = listOf(song(text, status = ImportPlan.Status.CONFLICTING)))))
        assertEquals("Song", dialog.values[Field.TITLE])
        assertEquals("Live", dialog.values[Field.SUBTITLE])
        assertEquals("Band", dialog.values[Field.ARTIST])
        assertEquals("", dialog.values[Field.ALBUM])
        assertTrue(dialog.isConverted)
        assertTrue(dialog.hasChords)
    }

    @Test
    fun `a song that declares no title leaves the form's title empty`() {
        val dialog = assertNotNull(newSongFromTextDialogOf(ImportPlan(songs = listOf(song("[G]Hello\n", sourceFileName = "Am C G.txt")))))
        assertEquals("", dialog.values[Field.TITLE])
        assertEquals("", dialog.values[Field.ARTIST])
    }

    @Test
    fun `lyrics alone say there are no chords`() {
        val dialog = assertNotNull(newSongFromTextDialogOf(ImportPlan(songs = listOf(song("Hello\nworld\n")))))
        assertFalse(dialog.hasChords)
    }

    @Test
    fun `a song whose name is taken is still a form`() {
        assertNotNull(newSongFromTextDialogOf(ImportPlan(songs = listOf(song("[G]Hello\n", status = ImportPlan.Status.CONFLICTING)))))
    }

    @Test
    fun `a song the library already holds is opened rather than made`() {
        val plan = ImportPlan(songs = listOf(song("[G]Hello\n", status = ImportPlan.Status.IDENTICAL)))
        assertNull(newSongFromTextDialogOf(plan))
        assertTrue(plan.isLoneSongWrittenAsIs)
    }

    @Test
    fun `a new song that declares its title and artist is written without a form`() {
        val plan = ImportPlan(songs = listOf(song("{title: Song}\n{artist: Band}\n[G]Hello\n")))
        assertNull(newSongFromTextDialogOf(plan))
        assertTrue(plan.isLoneSongWrittenAsIs)
    }

    @Test
    fun `a new song missing its title or its artist is still a form`() {
        assertNotNull(newSongFromTextDialogOf(ImportPlan(songs = listOf(song("{title: Song}\n[G]Hello\n")))))
        assertNotNull(newSongFromTextDialogOf(ImportPlan(songs = listOf(song("{title: }\n{artist: Band}\n[G]Hello\n")))))
        assertNotNull(newSongFromTextDialogOf(ImportPlan(songs = listOf(song("{artist: Band}\n[G]Hello\n")))))
    }

    @Test
    fun `several songs, or a song with something left out, are the import`() {
        assertNull(newSongFromTextDialogOf(ImportPlan(songs = listOf(song("[G]One\n"), song("[G]Two\n")))))
        assertNull(newSongFromTextDialogOf(ImportPlan(songs = listOf(song("[G]One\n")), oversizedFileNames = listOf("big.txt"))))
        assertNull(newSongFromTextDialogOf(ImportPlan(skippedFileNames = listOf("empty.txt"))))
    }
}
