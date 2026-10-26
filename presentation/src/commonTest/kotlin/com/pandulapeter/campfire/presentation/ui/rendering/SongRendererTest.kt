/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.rendering

import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.data.model.preferences.UserPreferences.Notation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class SongRendererTest {

    private val renderer = testSongRenderer()

    private fun spelling(accidentals: UserPreferences.Accidentals) = UserPreferences.ChordSpelling(accidentals = accidentals, notation = Notation.STANDARD)

    @Test
    fun `a key is moved by the file's transpose and the transposition, then spelled and drawn with signs`() {
        assertEquals("A♯", renderer.renderKey(key = "G", transpose = 0, transposition = 3, spelling = spelling(UserPreferences.Accidentals.SHARPS)))
        assertEquals("B♭", renderer.renderKey(key = "G", transpose = 0, transposition = 3, spelling = spelling(UserPreferences.Accidentals.FLATS)))
        assertEquals("B♭", renderer.renderKey(key = "G", transpose = 1, transposition = 2, spelling = spelling(UserPreferences.Accidentals.FLATS)))
    }

    @Test
    fun `a capo chart's sounding key names the key that sounds over its shapes, as its shapes' key does`() {
        val chords = "\n[D]a [G]b [A]c [Bm]d [D]e [G]f [A]g [D]h"
        val sounding = renderer.transposedSong("{key: G}\n{capo: 5}$chords", transposition = 5, spelling = UserPreferences.ChordSpelling.Default)
        assertEquals("G", sounding.metadata.key)
        assertEquals("G", renderer.transposedSong("{key: D}\n{capo: 5}$chords", transposition = 5, spelling = UserPreferences.ChordSpelling.Default).metadata.key)
        assertEquals("G", renderer.renderSong("{key: G}\n{capo: 5}$chords", transposition = 0, spelling = UserPreferences.ChordSpelling.Default).metadata.key)
    }

    @Test
    fun `a song with no key has no rendered key`() =
        assertNull(renderer.renderKey(key = null, transpose = 0, transposition = 3, spelling = UserPreferences.ChordSpelling.Default))

    @Test
    fun `a file survives the trip into the editor's notation and back`() {
        val text = "{key: Bb}\n[Bb]One [B]two [F]three"
        val editorText = renderer.editorTextOf(text, Notation.GERMAN)
        assertNotEquals(text, editorText)
        assertEquals(text, renderer.fileTextOf(editorText, Notation.GERMAN))
    }

    @Test
    fun `the editor's text is kept for the same file and notation and worked out again for another notation`() {
        val text = "[Bb]One [B]two"
        val german = renderer.editorTextOf(text, Notation.GERMAN)
        assertSame(german, renderer.editorTextOf(text, Notation.GERMAN))
        assertEquals(text, renderer.editorTextOf(text, Notation.STANDARD))
        assertEquals(german, renderer.editorTextOf(text, Notation.GERMAN))
    }

    @Test
    fun `the editor's text is transposed in its own notation`() = assertEquals(
        "[C]One",
        renderer.transposeText("[H]One", semitones = 1, accidentals = UserPreferences.Accidentals.ORIGINAL, notation = Notation.GERMAN),
    )
}
