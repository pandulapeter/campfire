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
    fun `a chord row with no chord in it is still transposed`() {
        val text = "{sot}\nAm   N.C.  G\ne|--0--3--|\n{eot}"

        assertEquals("{sot}\nBm   N.C.  A\ne|--2--5--|\n{eot}", ChordProTransposer.transposeText(text, 2, preferFlats = false))
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
