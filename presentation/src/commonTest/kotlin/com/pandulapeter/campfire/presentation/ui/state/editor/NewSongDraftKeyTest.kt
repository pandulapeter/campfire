/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state.editor

import com.pandulapeter.campfire.chordpro.ChordProParser
import com.pandulapeter.campfire.chordpro.scales.SongKeys
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.data.model.song.Song
import com.pandulapeter.campfire.presentation.ui.rendering.testSongRenderer
import com.pandulapeter.campfire.presentation.ui.songView.model.inferredKeyOfText
import com.pandulapeter.campfire.presentation.ui.songView.model.writtenKeyOfText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NewSongDraftKeyTest {

    private val renderer = testSongRenderer()

    private fun keyed(draft: String) = withInferredKey(draft, renderer::writtenKeyOfText)

    private fun draft(key: String = "", capo: Int = 0, body: String) =
        "{title: Riverlight}\n{artist: The Invented Band}\n{key: $key}\n{capo: $capo}\n{tempo: 120}\n{time: 4/4}\n\n$body"

    @Test
    fun `a draft naming no key fills its empty key line with the key its chords are in`() {
        val body = "{start_of_verse}\n[G]Down by the [C]river, [D]singing all [G]day\n[Em]Down by the [C]river, [D]singing all [G]day\n{end_of_verse}\n"
        assertEquals(draft(key = "G", body = body), keyed(draft(body = body)))
    }

    @Test
    fun `a draft with no key line gets one in the header after the title and the artist`() {
        assertEquals(
            "{title: Riverlight}\n{artist: The Invented Band}\n{key: G}\n\n[G]one [C]two [D]three [G]four\n",
            keyed("{title: Riverlight}\n{artist: The Invented Band}\n\n[G]one [C]two [D]three [G]four\n"),
        )
    }

    @Test
    fun `the key is spelled as the chords spell it`() {
        val body = "[Bb]one [Eb]two [F]three [Bb]four\n"
        assertEquals(draft(key = "Bb", body = body), keyed(draft(body = body)))
    }

    @Test
    fun `a key the draft declares is left as it is`() {
        val text = draft(key = "D", body = "[G]one [C]two [D]three [G]four\n")
        assertEquals(text, keyed(text))
    }

    @Test
    fun `a draft with no chords or with chords that name no key is left as it is`() {
        val lyrics = draft(body = "Down by the river\nSinging all day\n")
        assertEquals(lyrics, keyed(lyrics))
        val unclear = draft(body = "[C]one [F#]two [Bb]three [E]four\n")
        assertNull(renderer.writtenKeyOfText(unclear))
        assertEquals(unclear, keyed(unclear))
    }

    @Test
    fun `over a capo the key is the shapes' key that song details names as sounding just as it named the inferred one`() {
        val body = "[G]one [C]two [D]three [G]four [Em]five [C]six [D]seven [G]eight\n"
        val unkeyed = draft(capo = 2, body = body)
        val keyed = keyed(unkeyed)
        assertEquals(draft(key = "G", capo = 2, body = body), keyed)
        // Read as a capo chart's key, it is still the chords' own, so nothing reads it as the key that sounds.
        assertNull(SongKeys.capoChartKeyOf(ChordProParser.parse(keyed)))
        val song = Song(
            fileName = "riverlight.cho", title = "Riverlight", artist = "", key = null, transpose = 0, tags = emptyList(), languages = emptyList(),
            coverArtUrl = null, hasChords = true, canUpdateFileName = false, lastModified = 0, size = 0,
        )
        val sounding = 2
        assertEquals(
            renderer.renderKey(song = song, transposition = sounding, spelling = UserPreferences.ChordSpelling.Default, inferredKey = renderer.inferredKeyOfText(unkeyed)),
            renderer.renderKey(song = song.copy(key = "G"), transposition = sounding, spelling = UserPreferences.ChordSpelling.Default),
        )
        assertEquals("A", renderer.renderKey(song = song.copy(key = "G"), transposition = sounding, spelling = UserPreferences.ChordSpelling.Default))
    }

    @Test
    fun `a draft opening with a transposition is keyed as its chords are written before it`() {
        val text = "{title: Riverlight}\n{transpose: 2}\n[G]one [C]two [D]three [G]four\n"
        assertEquals("A", renderer.inferredKeyOfText(text))
        assertEquals("{title: Riverlight}\n{key: G}\n{transpose: 2}\n[G]one [C]two [D]three [G]four\n", keyed(text))
    }
}
