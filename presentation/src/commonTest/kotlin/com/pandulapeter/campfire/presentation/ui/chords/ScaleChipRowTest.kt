/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.chords

import com.pandulapeter.campfire.chordpro.chords.ChordProChords
import kotlin.test.Test
import kotlin.test.assertEquals

class ScaleChipRowTest {

    private fun chord(spelling: String?, name: String = spelling ?: "C") = SongChord(name = name, chord = ChordProChords.parse(name)!!, spelling = spelling)

    @Test
    fun `a chord's scales are played on its root as the chord spells it`() {
        assertEquals("Eb", chordRootName(chord("Ebm7")))
        assertEquals("D#", chordRootName(chord("D#m")))
        assertEquals("F#", chordRootName(chord("F#/A#")))
        assertEquals("B", chordRootName(chord("Bm")))
        assertEquals("Bb", chordRootName(chord("Bb")))
    }

    @Test
    fun `a chord with no spelling is played on its root as a sharp`() =
        assertEquals("C#", chordRootName(chord(spelling = null, name = "Db")))
}
