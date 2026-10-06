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

import com.pandulapeter.campfire.chordpro.ChordProChords
import com.pandulapeter.campfire.chordpro.ChordVoicings
import com.pandulapeter.campfire.chordpro.model.ChordInstrument
import com.pandulapeter.campfire.chordpro.model.ChordVoicing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChordDiagramGeometryTest {

    private fun fretted(frets: List<Int?>, fingers: List<Int>? = null, root: Int = 0) =
        chordDiagramGeometryOf(ChordVoicing.Fretted(frets, fingers), ChordInstrument.GUITAR, root) as ChordDiagramGeometry.Fretted

    @Test
    fun `an open shape is drawn from the nut in four frets`() {
        val c = fretted(listOf(null, 3, 2, 0, 1, 0))
        assertEquals(1, c.baseFret)
        assertEquals(4, c.fretCount)
        assertEquals(
            listOf(ChordDiagramGeometry.Fretted.Marker.MUTED, null, null, ChordDiagramGeometry.Fretted.Marker.OPEN, null, ChordDiagramGeometry.Fretted.Marker.OPEN),
            c.markers,
        )
        assertEquals(
            listOf(ChordDiagramGeometry.Fretted.Marker.OPEN_ROOT, null, null, ChordDiagramGeometry.Fretted.Marker.OPEN, ChordDiagramGeometry.Fretted.Marker.OPEN, ChordDiagramGeometry.Fretted.Marker.OPEN_ROOT),
            fretted(listOf(0, 2, 2, 0, 0, 0), root = 4).markers,
            "the open E strings of an Em are its root",
        )
        assertEquals(listOf(1 to 2, 2 to 1, 4 to 0), c.dots.map { it.string to it.row })
        assertEquals(listOf(true, false, true), c.dots.map { it.isRoot })
        assertEquals(emptyList(), c.barres)
    }

    @Test
    fun `a shape up the neck starts at its lowest fret and grows a fret where it spans five`() {
        val shape = fretted(listOf(null, 3, 5, 5, 5, 3))
        assertEquals(3, shape.baseFret)
        assertEquals(4, shape.fretCount)
        assertEquals(5, fretted(listOf(null, 5, 4, 5, 8, null)).fretCount)
    }

    @Test
    fun `a barre is found from the fingering, or from the lowest fret where nothing says`() {
        val f = fretted(listOf(1, 3, 3, 2, 1, 1), listOf(1, 3, 4, 2, 1, 1), root = 5)
        assertEquals(listOf(ChordDiagramGeometry.Fretted.Barre(row = 0, fromString = 0, toString = 5, finger = 1)), f.barres)
        // The barre's root strings keep their dot, in the root's color, and the others are the barre alone.
        assertEquals(listOf(0, 1, 2, 3, 5), f.dots.map { it.string })
        assertEquals(
            listOf(ChordDiagramGeometry.Fretted.Barre(row = 0, fromString = 1, toString = 5, finger = null)),
            fretted(listOf(null, 1, 3, 3, 3, 1)).barres,
        )
        assertEquals(
            listOf(ChordDiagramGeometry.Fretted.Barre(row = 0, fromString = 4, toString = 5, finger = null)),
            fretted(listOf(null, null, 3, 2, 1, 1)).barres,
        )
        assertEquals(emptyList(), fretted(listOf(1, 0, 3, 2, 1, 1)).barres.filter { it.fromString == 0 }, "an open string under it breaks the barre")
        val d = fretted(listOf(null, null, 0, 2, 3, 2))
        assertEquals(emptyList(), d.barres)
        assertEquals(listOf(3, 4, 5), d.dots.map { it.string })
        assertEquals(emptyList(), fretted(listOf(0, 2, 2, 0, 0, 0)).barres)
        assertEquals(emptyList(), fretted(listOf(null, 0, 2, 2, 2, 0)).barres)
        assertEquals(emptyList(), (chordDiagramGeometryOf(ChordVoicing.Fretted(listOf(0, 2, 3, 2)), ChordInstrument.UKULELE, root = 7) as ChordDiagramGeometry.Fretted).barres)
        assertEquals(
            listOf(ChordDiagramGeometry.Fretted.Barre(row = 0, fromString = 2, toString = 3, finger = null)),
            (chordDiagramGeometryOf(ChordVoicing.Fretted(listOf(3, 2, 1, 1)), ChordInstrument.UKULELE, root = 10) as ChordDiagramGeometry.Fretted).barres,
        )
    }

    @Test
    fun `a shape without fingers gets no barre its table fingering lacks`() {
        val roots = listOf("C", "C#", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B")
        val qualities = listOf("", "m", "7", "m7", "maj7", "sus2", "sus4", "6", "9", "dim7", "aug", "m7b5", "add9", "5")
        roots.forEach { root ->
            qualities.forEach { quality ->
                val chord = ChordProChords.parse(root + quality)!!
                listOf(ChordInstrument.GUITAR, ChordInstrument.UKULELE).forEach { instrument ->
                    ChordVoicings.all(chord, instrument).filterIsInstance<ChordVoicing.Fretted>().filter { it.fingers != null }.forEach { shape ->
                        val fingered = (chordDiagramGeometryOf(shape, instrument, chord.root) as ChordDiagramGeometry.Fretted).barres.map { it.copy(finger = null) }
                        val bare = (chordDiagramGeometryOf(shape.copy(fingers = null), instrument, chord.root) as ChordDiagramGeometry.Fretted).barres
                        bare.forEach { barre -> assertTrue(barre.copy(finger = null) in fingered, "$root$quality $instrument ${shape.frets}") }
                    }
                }
            }
        }
    }

    @Test
    fun `a keyboard is drawn in as many octaves as its keys reach, two at least`() {
        val c = chordDiagramGeometryOf(ChordVoicing.Keys(listOf(0, 4, 7)), ChordInstrument.KEYBOARD, root = 0) as ChordDiagramGeometry.Keyboard
        assertEquals(2, c.octaves)
        assertEquals(setOf(0), c.roots)
        val slash = chordDiagramGeometryOf(ChordVoicing.Keys(listOf(14, 18, 21), bass = 6), ChordInstrument.KEYBOARD, root = 2) as ChordDiagramGeometry.Keyboard
        assertEquals(2, slash.octaves)
        assertEquals(6, slash.bass)
        assertEquals(setOf(14), slash.roots)
        assertEquals(3, (chordDiagramGeometryOf(ChordVoicing.Keys(listOf(23, 27, 30)), ChordInstrument.KEYBOARD, root = 11) as ChordDiagramGeometry.Keyboard).octaves)
    }

    @Test
    fun `a shape out of all reason is drawn bounded`() {
        val g = fretted(listOf(3, 2, 0, 0, 0, 99999999))
        assertEquals(24, g.fretCount)
        assertTrue(g.dots.none { it.row >= 24 })
        val keys = chordDiagramGeometryOf(ChordVoicing.Keys(listOf(0, 4, 99999999)), ChordInstrument.KEYBOARD, root = 0) as ChordDiagramGeometry.Keyboard
        assertEquals(4, keys.octaves)
        assertEquals(setOf(0, 4), keys.keys)
        assertEquals(null, (chordDiagramGeometryOf(ChordVoicing.Keys(listOf(4, 7), bass = -5), ChordInstrument.KEYBOARD, root = 0) as ChordDiagramGeometry.Keyboard).bass)
    }
}
