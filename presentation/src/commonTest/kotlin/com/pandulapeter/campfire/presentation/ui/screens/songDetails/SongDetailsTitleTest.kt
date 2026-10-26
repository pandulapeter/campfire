/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails

import com.pandulapeter.campfire.data.model.song.Song
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.presentation.ui.playing.PlayingOverridesSnapshot
import com.pandulapeter.campfire.presentation.ui.playing.SongOverrides
import com.pandulapeter.campfire.presentation.ui.playing.SongPlace
import com.pandulapeter.campfire.presentation.ui.playing.Transpositions
import com.pandulapeter.campfire.presentation.ui.rendering.testSongRenderer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SongDetailsTitleTest {

    private val renderer = testSongRenderer()
    private val song = Song(
        fileName = "song.cho",
        title = "Song",
        artist = "",
        key = "G",
        transpose = 0,
        tags = emptyList(),
        languages = emptyList(),
        coverArtUrl = null,
        hasChords = true,
        canUpdateFileName = false,
        lastModified = 0,
        size = 0,
    )

    @Test
    fun `the header names the key the song sounds in where it is read`() {
        val overrides = PlayingOverridesSnapshot(transpositions = Transpositions(SongOverrides<Int>().with(SongPlace("song.cho", "set.setlist.json"), 2)))

        assertEquals("G", keyOf(song, setlistFileName = null, overrides = overrides))
        assertEquals("A", keyOf(song, setlistFileName = "set.setlist.json", overrides = overrides))
    }

    @Test
    fun `a capo chart keyed by the sounding key is named by it`() {
        assertEquals("G", keyOf(song.copy(key = "G", capo = 5, capoChartKey = "D")))
        assertEquals("C", keyOf(song.copy(key = "G", capo = 5)))
    }

    @Test
    fun `the header names no key with the chords off or for a song without chords`() {
        assertNull(keyOf(song, shouldShowChords = false))
        assertNull(keyOf(song.copy(hasChords = false)))
        assertNull(keyOf(null))
    }

    @Test
    fun `a song whose file names no key is named by the key its chords were found in, moved as a written one`() {
        val unkeyed = song.copy(key = null)
        val capoed = PlayingOverridesSnapshot(capos = SongOverrides<Int>().with(SongPlace("song.cho", null), 2))
        val transposed = PlayingOverridesSnapshot(transpositions = Transpositions(SongOverrides<Int>().with(SongPlace("song.cho", null), 2)))

        assertNull(keyOf(unkeyed))
        assertEquals("D", keyOf(unkeyed, inferredKey = "D"))
        assertEquals("Dm", keyOf(unkeyed, inferredKey = "Dm", overrides = capoed), "a capo put on keeps the pitch")
        assertEquals("Em", keyOf(unkeyed.copy(capo = 2), inferredKey = "Dm"), "the file's own capo is pitch")
        assertEquals("Em", keyOf(unkeyed, inferredKey = "Dm", overrides = transposed))
        assertEquals("G", keyOf(song, inferredKey = "D"))
        assertNull(keyOf(unkeyed, inferredKey = "D", shouldShowChords = false))
    }

    private fun keyOf(
        song: Song?,
        setlistFileName: String? = null,
        shouldShowChords: Boolean = true,
        overrides: PlayingOverridesSnapshot = PlayingOverridesSnapshot(),
        inferredKey: String? = null,
    ) = headerKeyOf(
        song = song,
        setlistFileName = setlistFileName,
        shouldShowChords = shouldShowChords,
        overrides = overrides,
        songRenderer = renderer,
        chordSpelling = UserPreferences.ChordSpelling.Default,
        inferredKey = inferredKey,
    )
}
