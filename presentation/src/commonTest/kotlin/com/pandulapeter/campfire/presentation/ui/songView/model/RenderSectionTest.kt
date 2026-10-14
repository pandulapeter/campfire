/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songView.model

import com.pandulapeter.campfire.chordpro.model.ChordTuning
import com.pandulapeter.campfire.presentation.ui.chords.ChordCell
import com.pandulapeter.campfire.presentation.ui.chords.SelectedShape
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class RenderSectionTest {

    @Test
    fun `a folded chords section is its header alone`() {
        val cell = ChordCell(
            name = "G",
            tuning = ChordTuning.GUITAR,
            root = 7,
            selection = SelectedShape(null, SelectedShape.Source.DEFAULT),
        )
        assertEquals(1, RenderSection.Chords(cells = List(5) { cell }, isFolded = true).itemCount)
        val unfolded = RenderSection.Chords(cells = List(5) { cell }, isFolded = false)
        assertEquals(5, unfolded.itemCount)
        assertContentEquals(intArrayOf(0, 1, 2, 3, 4), unfolded.chunkStarts)
    }
}
