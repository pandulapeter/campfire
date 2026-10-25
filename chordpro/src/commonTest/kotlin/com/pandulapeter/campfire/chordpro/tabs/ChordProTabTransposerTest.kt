/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.tabs

import com.pandulapeter.campfire.chordpro.chords.ChordProTransposer
import kotlin.test.Test
import kotlin.test.assertEquals

class ChordProTabTransposerTest {

    @Test
    fun `a repeat count written before its x is not a fret`() {
        assertEquals("{sot}\ne|--2--4--| 3x\n{eot}", ChordProTransposer.transposeText("{sot}\ne|--0--2--| 3x\n{eot}", 2))
        assertEquals("{sot}\ne|--2--4--| (3x)\n{eot}", ChordProTransposer.transposeText("{sot}\ne|--0--2--| (3x)\n{eot}", 2))
        assertEquals("{sot}\ne|--5x--|\n{eot}", ChordProTransposer.transposeText("{sot}\ne|--3x--|\n{eot}", 2))
    }

    @Test
    fun `a tab with an open string moves up by the rest of the octave whenever it is transposed down`() {
        (1..11).forEach { semitones ->
            val transposed = ChordProTabTransposer.transpose(listOf("e|--0--3--|"), -semitones) { it }.single()

            assertEquals(listOf(12 - semitones, 15 - semitones), Regex("\\d+").findAll(transposed).map { it.value.toInt() }.toList())
        }
    }

    @Test
    fun `a chord row with no chord in it is still transposed`() {
        val text = "{sot}\nAm   N.C.  G\ne|--0--3--|\n{eot}"

        assertEquals("{sot}\nBm   N.C.  A\ne|--2--5--|\n{eot}", ChordProTransposer.transposeText(text, 2, preferFlats = false))
    }

    @Test
    fun `a chord row written with non-breaking spaces is transposed with the frets`() {
        val text = "{start_of_tab}\nAm\u00A0   G\ne|--0--2--|\nB|--1--3--|\nG|--2--0--|\n{end_of_tab}"

        assertEquals(
            "{start_of_tab}\nBm\u00A0   A\ne|--2--4--|\nB|--3--5--|\nG|--4--2--|\n{end_of_tab}",
            ChordProTransposer.transposeText(text, 2, preferFlats = false),
        )
    }

    @Test
    fun `a prose line in a tab is still left alone`() {
        val text = "{sot}\nTuning:\u00A0D\u00A0A\u00A0D\u00A0G\u00A0A\u00A0D\ne|--0--|\n{eot}"

        assertEquals("{sot}\nTuning:\u00A0D\u00A0A\u00A0D\u00A0G\u00A0A\u00A0D\ne|--2--|\n{eot}", ChordProTransposer.transposeText(text, 2))
    }

    @Test
    fun `every abbreviation of no chord is a marker`() {
        val lines = listOf("C NC D n.c E N.C", "e|--0--|")

        assertEquals(
            listOf("D NC E n.c F# N.C", "e|--2--|"),
            ChordProTabTransposer.transpose(lines, 2) { name -> ChordProTransposer.transposeChord(name, 2, preferFlats = false) },
        )
    }

    @Test
    fun `a fret that grows a digit widens its column on every string`() {
        assertEquals(
            listOf("e|-15-17-|", "B|-------|"),
            ChordProTabTransposer.transpose(listOf("e|-3-5-|", "B|-----|"), 12) { it },
        )
    }

    @Test
    fun `a fret that loses a digit narrows its column on every string`() {
        assertEquals(
            listOf("e|--9--|", "B|-----|"),
            ChordProTabTransposer.transpose(listOf("e|--10--|", "B|------|"), -1) { it },
        )
    }

    @Test
    fun `a column as wide as its widest fret does not move when a narrower one grows into it`() {
        assertEquals(
            listOf("e|--11--|", "B|--10--|"),
            ChordProTabTransposer.transpose(listOf("e|--10--|", "B|--9---|"), 1) { it },
        )
    }

    @Test
    fun `a tab transposed up and back down is the tab it was`() {
        val tab = listOf(
            "  Am       C",
            "e|-5---7h9-----|",
            "B|---9-------8-|",
            "G|-----------9-|",
        )

        (1..5).forEach { semitones ->
            val up = ChordProTabTransposer.transpose(tab, semitones) { it }

            assertEquals(tab, ChordProTabTransposer.transpose(up, -semitones) { it }, "up and down by $semitones")
        }
    }

    @Test
    fun `a chord name stays over the note it is written over when a column before it widens`() {
        assertEquals(
            listOf("      C", "e|-10-1-|", "B|----1-|"),
            ChordProTabTransposer.transpose(listOf("     C", "e|-9-0-|", "B|---0-|"), 1) { it },
        )
    }
}
