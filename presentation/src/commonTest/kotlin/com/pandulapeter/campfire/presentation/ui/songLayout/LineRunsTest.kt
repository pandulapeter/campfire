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

import com.pandulapeter.campfire.chordpro.model.ChordProLine
import com.pandulapeter.campfire.chordpro.model.GridToken
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LineRunsTest {

    private val lyrics = ChordProLine.Lyrics(text = "Lyrics", chords = emptyList())

    private fun tab(text: String = "e|---|", continuesEnvironment: Boolean = false, label: String? = null) =
        ChordProLine.Tab(text = text, continuesEnvironment = continuesEnvironment, label = label)

    private fun grid(label: String? = null) = ChordProLine.Grid(tokens = listOf(GridToken.Chord("Am")), label = label)

    @Test
    fun `lines are split into runs of tablature, of grid lines and of everything else, and at a blank line`() {
        val first = tab()
        val second = tab(continuesEnvironment = true)
        val gridLine = grid()

        assertEquals(
            listOf(listOf(lyrics), listOf(first, second), listOf(gridLine), listOf(ChordProLine.Blank, lyrics)),
            listOf(lyrics, first, second, gridLine, ChordProLine.Blank, lyrics).groupIntoRuns(),
        )
        assertEquals(listOf(listOf(first), listOf(ChordProLine.Blank), listOf(first)), listOf(first, ChordProLine.Blank, first).groupIntoRuns())
    }

    @Test
    fun `two environments labelled differently are two runs`() {
        val riff = tab(label = "Riff")
        val solo = tab(label = "Solo")

        assertEquals(listOf(listOf(riff), listOf(solo)), listOf(riff, solo).groupIntoRuns())
    }

    @Test
    fun `two environments written back to back under one label are one run`() {
        val first = tab(label = "Riff")
        val second = tab(continuesEnvironment = true, label = "Riff")
        val third = tab(continuesEnvironment = false, label = "Riff")

        assertEquals(listOf(listOf(first, second, third)), listOf(first, second, third).groupIntoRuns())
    }

    @Test
    fun `a section of tablature is folded whole with a blank line in it, and a mixed one is not`() {
        assertEquals(FoldableKind.TAB, listOf(tab(), ChordProLine.Blank, tab()).wholeFoldableKind())
        assertNull(listOf(tab(), lyrics).wholeFoldableKind())
    }

    @Test
    fun `a grid is as many pieces as its lines and tablature between one and twenty four`() {
        assertEquals(3, listOf(grid(), grid(), grid()).runSlotCount(FoldableKind.GRID))
        assertEquals(1, listOf(tab(text = "")).runSlotCount(FoldableKind.TAB))
        assertEquals(24, List(30) { tab() }.runSlotCount(FoldableKind.TAB))
    }
}
