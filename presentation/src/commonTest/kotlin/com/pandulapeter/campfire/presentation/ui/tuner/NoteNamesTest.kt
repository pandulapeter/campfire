/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.tuner

import com.pandulapeter.campfire.chordpro.ChordNotation
import kotlin.test.Test
import kotlin.test.assertEquals

class NoteNamesTest {

    @Test
    fun `a note is named with sharps and its octave in scientific numbering`() {
        assertEquals("A4", noteNameWithOctave(69, ChordNotation.STANDARD))
        assertEquals("E2", noteNameWithOctave(40, ChordNotation.STANDARD))
        assertEquals("C#3", noteNameWithOctave(49, ChordNotation.STANDARD))
        assertEquals("C-1", noteNameWithOctave(0, ChordNotation.STANDARD))
        assertEquals("B0", noteNameWithOctave(23, ChordNotation.STANDARD))
    }

    @Test
    fun `a note is named in the reader's notation, and in letters for a numbering`() {
        assertEquals("H", noteName(59, ChordNotation.GERMAN))
        assertEquals("E", noteName(40, ChordNotation.GERMAN))
        assertEquals("Mi", noteName(40, ChordNotation.LATIN))
        assertEquals("E", noteName(40, ChordNotation.NASHVILLE))
        assertEquals("G", noteName(55, ChordNotation.ROMAN))
    }
}
