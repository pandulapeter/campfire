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
    fun `the header names no key with the chords off or for a song without chords`() {
        assertNull(keyOf(song, shouldShowChords = false))
        assertNull(keyOf(song.copy(hasChords = false)))
        assertNull(keyOf(null))
    }

    private fun keyOf(
        song: Song?,
        setlistFileName: String? = null,
        shouldShowChords: Boolean = true,
        overrides: PlayingOverridesSnapshot = PlayingOverridesSnapshot(),
    ) = headerKeyOf(
        song = song,
        setlistFileName = setlistFileName,
        shouldShowChords = shouldShowChords,
        overrides = overrides,
        songRenderer = renderer,
        chordSpelling = UserPreferences.ChordSpelling.Default,
    )
}
