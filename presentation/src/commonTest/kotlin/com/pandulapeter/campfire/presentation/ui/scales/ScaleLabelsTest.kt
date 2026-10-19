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

import com.pandulapeter.campfire.chordpro.chords.ChordNotation
import com.pandulapeter.campfire.chordpro.scales.Scales
import kotlin.test.Test
import kotlin.test.assertEquals

class ScaleLabelsTest {

    @Test
    fun `a scale's notes are drawn with accidental signs in every notation`() {
        val major = Scales.fromId("major")!!
        assertEquals(listOf("E", "F♯", "G♯", "A", "B", "C♯", "D♯"), shownScaleNoteNames(major, "E", ChordNotation.STANDARD))
        assertEquals(listOf("Fa", "Sol", "La", "Si♭", "Do", "Re", "Mi"), shownScaleNoteNames(major, "F", ChordNotation.LATIN))
    }

    @Test
    fun `a degree is drawn with accidental signs`() {
        assertEquals("♭♭7", displayedDegree("bb7"))
        assertEquals("♯4", displayedDegree("#4"))
    }
}
