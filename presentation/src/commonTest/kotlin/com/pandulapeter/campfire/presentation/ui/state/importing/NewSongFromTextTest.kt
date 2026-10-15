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

import com.pandulapeter.campfire.data.model.importing.ImportPlan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

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
    fun `one song the library does not hold is a new song, declared title and artist or not`() {
        val text = "{title: Song}\n{artist: Band}\n[G]Hello\n"
        assertEquals(text, ImportPlan(songs = listOf(song(text))).newSongOf()?.text)
        assertNotNull(ImportPlan(songs = listOf(song("[G]Hello\n"))).newSongOf())
    }

    @Test
    fun `a song whose name is taken is a new song all the same`() {
        assertNotNull(ImportPlan(songs = listOf(song("[G]Hello\n", status = ImportPlan.Status.CONFLICTING))).newSongOf())
    }

    @Test
    fun `a song the library already holds is opened rather than made`() {
        assertNull(ImportPlan(songs = listOf(song("[G]Hello\n", status = ImportPlan.Status.IDENTICAL))).newSongOf())
    }

    @Test
    fun `several songs, or a song with something left out, are the import`() {
        assertNull(ImportPlan(songs = listOf(song("[G]One\n"), song("[G]Two\n"))).newSongOf())
        assertNull(ImportPlan(songs = listOf(song("[G]One\n")), oversizedFileNames = listOf("big.txt")).newSongOf())
        assertNull(ImportPlan(skippedFileNames = listOf("empty.txt")).newSongOf())
    }
}
