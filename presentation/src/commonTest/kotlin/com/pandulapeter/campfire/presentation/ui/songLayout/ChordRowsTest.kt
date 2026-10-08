/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songLayout

import com.pandulapeter.campfire.chordpro.model.ChordInstrument
import com.pandulapeter.campfire.presentation.ui.chords.ChordCell
import com.pandulapeter.campfire.presentation.ui.chords.MAX_SONG_CHORDS
import com.pandulapeter.campfire.presentation.ui.chords.SelectedShape
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.RenderSection
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChordRowsTest {

    @Test
    fun cellsFlowIntoRowsAtTheWidth() {
        assertContentEquals(intArrayOf(0, 2), chordRowStarts(intArrayOf(56, 56, 56), gap = 6, width = 118))
        assertContentEquals(intArrayOf(0, 1, 2), chordRowStarts(intArrayOf(56, 56, 56), gap = 6, width = 117))
        assertContentEquals(intArrayOf(0), chordRowStarts(intArrayOf(56, 56, 56), gap = 6, width = Int.MAX_VALUE))
    }

    @Test
    fun theCellsAreSharedOutEvenlyBetweenAsFewRowsAsTheWidthAllows() {
        // Six of nine fit on a line: two rows still, but five over four rather than six over three.
        assertContentEquals(intArrayOf(0, 5), chordRowStarts(IntArray(9) { 56 }, gap = 6, width = 6 * 56 + 5 * 6))
        assertContentEquals(intArrayOf(0, 4, 7), chordRowStarts(IntArray(10) { 56 }, gap = 6, width = 4 * 56 + 3 * 6))
        assertContentEquals(intArrayOf(0, 2), chordRowStarts(intArrayOf(100, 56, 56, 56), gap = 6, width = 250))
    }

    @Test
    fun aCellWiderThanTheWidthIsARowOfItsOwn() {
        assertContentEquals(intArrayOf(0, 1, 2), chordRowStarts(intArrayOf(56, 200, 56), gap = 6, width = 120))
        assertEquals(0, chordRowStarts(IntArray(0), gap = 6, width = 120).size)
    }

    @Test
    fun aSlotHoldsTheRowItsIndexNamesAndTheLastOneTheRest() {
        assertEquals(1..1, chordSlotRows(rowCount = 5, firstSlot = 1, lastSlot = 1, isLastSlot = false))
        assertEquals(3..4, chordSlotRows(rowCount = 5, firstSlot = 3, lastSlot = 3, isLastSlot = true))
        assertTrue(chordSlotRows(rowCount = 2, firstSlot = 3, lastSlot = 3, isLastSlot = false).isEmpty())
        assertEquals(0..4, chordSlotRows(rowCount = 5, firstSlot = 0, lastSlot = 47, isLastSlot = true))
    }

    @Test
    fun theSlotsAreAsManyAsTheCellsUpToTheCap() {
        assertEquals(1, chordSlotCount(1))
        assertEquals(5, chordSlotCount(5))
        assertEquals(48, chordSlotCount(48))
        assertEquals(MAX_CHORD_SLOTS, chordSlotCount(60))
        assertEquals(MAX_SONG_CHORDS, MAX_CHORD_SLOTS)
    }

    @Test
    fun aFoldedChordsSectionIsItsHeaderAlone() {
        val cell = ChordCell(
            name = "G",
            soundingName = null,
            instrument = ChordInstrument.GUITAR,
            root = 7,
            selection = SelectedShape(null, SelectedShape.Source.DEFAULT),
        )
        assertEquals(1, RenderSection.Chords(cells = List(5) { cell }, isFolded = true).itemCount)
        val unfolded = RenderSection.Chords(cells = List(5) { cell }, isFolded = false)
        assertEquals(5, unfolded.itemCount)
        assertContentEquals(intArrayOf(0, 1, 2, 3, 4), unfolded.chunkStarts)
    }
}
