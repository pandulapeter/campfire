/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro

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
}
