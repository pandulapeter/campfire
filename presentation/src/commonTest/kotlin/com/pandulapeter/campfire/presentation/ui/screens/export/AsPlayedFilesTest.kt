/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.export

import com.pandulapeter.campfire.data.model.preferences.PrintSettings
import com.pandulapeter.campfire.presentation.ui.playing.SongAsPlayed
import com.pandulapeter.campfire.presentation.ui.print.PrintSong
import com.pandulapeter.campfire.presentation.ui.print.PrintSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AsPlayedFilesTest {

    private val transposed = SongAsPlayed(semitones = 2, capo = null, tempo = null, transposition = 2, preferFlats = null)
    private val songs = listOf(
        PrintSong(fileName = "a.cho", title = "A", artist = null, song = null, asPlayed = transposed),
        PrintSong(fileName = "b.cho", title = "B", artist = null, song = null),
    )

    @Test
    fun `songs played differently are written as played, by default, and the others as stored`() {
        val source = PrintSource(title = "Songs", kind = PrintSource.Kind.SONGS, songs = songs)
        assertTrue(isAsPlayedOffered(source))
        assertEquals(mapOf("a.cho" to transposed), asPlayedFilesOf(source, PrintSettings()))
        assertEquals(emptyMap(), asPlayedFilesOf(source, PrintSettings(exportsAsPlayed = false)))
    }

    @Test
    fun `a setlist's zip is never written as played, its manifest saying how each song is played`() {
        val setlist = PrintSource(title = "Gig", kind = PrintSource.Kind.SETLIST, songs = songs)
        assertFalse(isAsPlayedOffered(setlist))
        assertEquals(emptyMap(), asPlayedFilesOf(setlist, PrintSettings()))
    }

    @Test
    fun `nothing is offered where every song is played as its file says`() =
        assertFalse(isAsPlayedOffered(PrintSource(title = "B", songs = songs.drop(1))))
}
