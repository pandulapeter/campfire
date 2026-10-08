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

import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.data.model.domain.UserPreferences.Notation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class SongRendererTest {

    private val renderer = testSongRenderer()

    private fun spelling(accidentals: UserPreferences.Accidentals) = UserPreferences.ChordSpelling(accidentals = accidentals, notation = Notation.STANDARD)

    @Test
    fun `a key is moved by the file's transpose, the reader's transposition and the capo, then spelled`() {
        assertEquals("A#", renderer.renderKey(key = "G", transpose = 0, transposition = 2, capo = 1, spelling = spelling(UserPreferences.Accidentals.SHARPS)))
        assertEquals("Bb", renderer.renderKey(key = "G", transpose = 0, transposition = 2, capo = 1, spelling = spelling(UserPreferences.Accidentals.FLATS)))
        assertEquals("Bb", renderer.renderKey(key = "G", transpose = 1, transposition = 1, capo = 1, spelling = spelling(UserPreferences.Accidentals.FLATS)))
    }

    @Test
    fun `a song with no key has no rendered key`() =
        assertNull(renderer.renderKey(key = null, transpose = 0, transposition = 2, capo = 1, spelling = UserPreferences.ChordSpelling.Default))

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
