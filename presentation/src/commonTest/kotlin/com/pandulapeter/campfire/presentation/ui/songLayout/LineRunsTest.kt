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
import com.pandulapeter.campfire.chordpro.tabs.ChordProTabWrapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LineRunsTest {

    private val lyrics = ChordProLine.Lyrics(text = "Lyrics", chords = emptyList())

    private fun tab(text: String = "e|---|", continuesEnvironment: Boolean = false, label: String? = null) =
        ChordProLine.Tab(text = text, continuesEnvironment = continuesEnvironment, label = label)

    private fun grid(label: String? = null) = ChordProLine.Grid(tokens = listOf(GridToken.Chord("Am")), label = label)

    private fun abc(text: String = "|:GABc dedB:|") = ChordProLine.Verbatim(text = text, environment = "abc")

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
    fun `a grid is as many pieces as its lines and tablature as many as its lines and its longest line's wraps`() {
        assertEquals(3, listOf(grid(), grid(), grid()).runSlotCount(FoldableKind.GRID))
        assertEquals(1, listOf(tab(text = "")).runSlotCount(FoldableKind.TAB))
        assertEquals(30, List(30) { tab(text = "e|---|") }.runSlotCount(FoldableKind.TAB))
        assertEquals(6 + 2, listOf("e", "B", "G", "D", "A", "E").map { tab(text = "$it|" + "-".repeat(22) + "|") }.runSlotCount(FoldableKind.TAB))
    }

    @Test
    fun `a run of several systems has a piece for every row each of its systems can wrap into`() {
        val chord = ("Am" + " ".repeat(10) + "C           G           ".repeat(6).take(136)).padEnd(150)
        val staff = "-0---2---3-|".repeat(13).take(148)
        val system = listOf(chord) + listOf("e", "B", "G", "D", "A", "E").map { "$it|$staff" }
        val lines = List(20) { system }.flatten()
        val run = lines.mapIndexed { index, line -> tab(text = line, continuesEnvironment = index > 0) }

        listOf(20, 16).forEach { maxColumns ->
            val rows = ChordProTabWrapper.wrapLines(lines, maxColumns)
            assertTrue(run.runSlotCount(FoldableKind.TAB) >= rows.size, "${rows.size} rows at $maxColumns columns")
        }
    }

    @Test
    fun `a run of one system has a piece for every line and for every wrap of its longest`() {
        val system = listOf("  Am" + " ".repeat(30) + "C") + listOf("e", "B", "G", "D", "A", "E").map { "$it|" + "-0---2---3-|".repeat(4) }

        assertEquals(7 + 50 / 12, system.map { tab(text = it) }.runSlotCount(FoldableKind.TAB))
    }

    @Test
    fun `a long tune is cut into a piece for each line and for each wrap of every line, so no column holds the rest whole`() {
        assertEquals(40 * 3, List(40) { abc("abc def gab|" + "abc def gab|" + "abc|") }.runSlotCount(FoldableKind.VERBATIM))
        assertEquals(500, List(1_000) { abc() }.runSlotCount(FoldableKind.VERBATIM))
    }

    @Test
    fun `verbatim lines are a run of their own, folded whole as their section, a short one in as many pieces as tablature`() {
        val first = abc("X:1")
        val second = abc()

        assertEquals(FoldableKind.VERBATIM, first.foldableKind())
        assertEquals(listOf(listOf(first, second), listOf(ChordProLine.Blank), listOf(first)), listOf(first, second, ChordProLine.Blank, first).groupIntoRuns())
        assertEquals(FoldableKind.VERBATIM, listOf(first, ChordProLine.Blank, second).wholeFoldableKind())
        assertNull(listOf(first, lyrics).wholeFoldableKind())
        assertEquals(listOf(tab(), tab()).runSlotCount(FoldableKind.TAB), listOf(abc("e|---|"), abc("e|---|")).runSlotCount(FoldableKind.VERBATIM))
    }

    @Test
    fun `a verbatim run with no label of its own is named after its environment`() {
        val labels = DefaultSectionLabels(
            verse = "Verse", chorus = "Chorus", bridge = "Bridge", tab = "Tab", grid = "Grid", abc = "ABC notation", lilyPond = "LilyPond",
            svg = "SVG", intro = "Intro", preChorus = "Pre-chorus", solo = "Solo", outro = "Outro",
        )

        assertEquals("ABC notation", labels.labelOf(FoldableKind.VERBATIM, listOf(ChordProLine.Blank, abc())))
        assertEquals("LilyPond", labels.labelOf(FoldableKind.VERBATIM, listOf(ChordProLine.Verbatim("c4 d e", "ly"))))
        assertNull(abc().environmentLabel)
    }
}
