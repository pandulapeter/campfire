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
import kotlin.test.Test
import kotlin.test.assertEquals

class ScaleTonesTest {

    @Test
    fun `a scale is played up from its root over middle C and ends on the octave`() {
        val major = Scales.fromId("major")!!
        assertEquals(listOf(62, 64, 66, 67, 69, 71, 73, 74), scaleToneNotes(major, root = 2))
    }

    @Test
    fun `a scale on B starts over middle C and climbs past the next C`() {
        val pentatonic = Scales.fromId("minor_pentatonic")!!
        assertEquals(listOf(71, 74, 76, 78, 81, 83), scaleToneNotes(pentatonic, root = 11))
    }
}
