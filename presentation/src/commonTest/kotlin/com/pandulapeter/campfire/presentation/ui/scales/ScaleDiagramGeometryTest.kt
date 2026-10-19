/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.scales

import com.pandulapeter.campfire.chordpro.scales.Scales
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.tuner.api.model.InstrumentTuning
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ScaleDiagramGeometryTest {

    private val major = Scales.fromId("major")!!

    private fun fretboard(strings: List<Int>, instrument: UserPreferences.Instrument = UserPreferences.Instrument.GUITAR, root: Int = 0) =
        assertIs<ScaleDiagramGeometry.Fretboard>(scaleDiagramGeometryOf(major, root, strings, instrument))

    @Test
    fun `the roots of C major are where a guitarist finds them, the high E on top`() {
        val neck = fretboard(InstrumentTuning.GUITAR.strings)
        val roots = neck.notes.filter { it.isRoot }.map { it.row to it.fret }.toSet()
        // Rows from the top: high E, B, G, D, A, low E.
        assertTrue(roots.containsAll(listOf(5 to 8, 4 to 3, 3 to 10, 2 to 5, 1 to 1, 0 to 8)))
        assertEquals(6, neck.strings)
        assertEquals(1f, neck.stringWeights[5])
        assertEquals(0f, neck.stringWeights[0])
    }

    @Test
    fun `every note placed is a note of the scale`() {
        val strings = InstrumentTuning.GUITAR.strings.reversed()
        fretboard(InstrumentTuning.GUITAR.strings, root = 2).notes.forEach { note ->
            assertEquals(major.intervals[note.degree], (strings[note.row] + note.fret - 2).mod(12))
        }
    }

    @Test
    fun `drop D moves the low string's notes by two frets`() {
        val standard = fretboard(InstrumentTuning.GUITAR.strings).notes.filter { it.row == 5 }.map { it.fret }
        val dropD = fretboard(InstrumentTuning.GUITAR_DROP_D.strings).notes.filter { it.row == 5 }.map { it.fret }
        assertEquals(standard.filter { it + 2 <= FRET_COUNT }.map { it + 2 }, dropD.filter { it >= 2 })
    }

    @Test
    fun `the banjo's drone starts at the fifth fret, at the bottom`() {
        val neck = fretboard(InstrumentTuning.BANJO.strings, UserPreferences.Instrument.BANJO)
        val drone = neck.strings - 1
        assertEquals(5, neck.stringStarts[drone])
        assertTrue(neck.notes.filter { it.row == drone }.all { it.fret >= 5 })
        // Its open note is the G it is tuned to: a G is the fifth of C major.
        assertTrue(neck.notes.any { it.row == drone && it.fret == 5 && it.degree == 4 })
    }

    @Test
    fun `a ukulele has its A on top and its re-entrant g at the bottom, and a violin has no frets`() {
        val ukulele = fretboard(InstrumentTuning.UKULELE.strings, UserPreferences.Instrument.UKULELE)
        assertTrue(ukulele.notes.any { it.row == 0 && it.fret == 3 && it.isRoot })
        assertTrue(ukulele.notes.any { it.row == 3 && it.fret == 5 && it.isRoot })
        assertTrue(fretboard(InstrumentTuning.VIOLIN.strings, UserPreferences.Instrument.VIOLIN).isFretless)
    }

    @Test
    fun `the keyboard and other instruments draw the scale over two octaves`() {
        listOf(UserPreferences.Instrument.KEYBOARD, UserPreferences.Instrument.OTHER).forEach { instrument ->
            val keyboard = assertIs<ScaleDiagramGeometry.Keyboard>(scaleDiagramGeometryOf(major, 0, null, instrument)).keyboard
            assertEquals(setOf(0, 2, 4, 5, 7, 9, 11, 12, 14, 16, 17, 19, 21, 23), keyboard.keys)
            assertEquals(setOf(0, 12), keyboard.roots)
        }
    }
}
