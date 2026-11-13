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

import com.pandulapeter.campfire.chordpro.ChordProParser
import com.pandulapeter.campfire.chordpro.model.ChordProBlock
import com.pandulapeter.campfire.chordpro.model.ChordProLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GridBarsTest {

    /** The chart lines of a grid environment holding [lines], as the parser reads them. */
    private fun grid(vararg lines: String) = gridBarLinesOf(
        ChordProParser.parse(lines.joinToString("\n", prefix = "{start_of_grid}\n", postfix = "\n{end_of_grid}"))
            .blocks.filterIsInstance<ChordProBlock.Section>()
            .flatMap { it.lines }
            .filterIsInstance<ChordProLine.Grid>()
            .map { it.tokens },
    )

    private fun chord(vararg names: String, beats: Int) = GridSegment.Chord(names.toList(), beats)

    @Test
    fun `a chord lasts until the next one`() {
        val bars = grid("| Dm . Am . | Am . E7 Am |").single().bars
        assertEquals(listOf(chord("Dm", beats = 2), chord("Am", beats = 2)), bars[0].segments)
        assertEquals(listOf(chord("Am", beats = 2), chord("E7", beats = 1), chord("Am", beats = 1)), bars[1].segments)
        assertEquals(listOf(4, 4), bars.map { it.beats })
    }

    @Test
    fun `beats before a bar's first chord hold the chord before`() {
        val bar = grid("| C . . . | . . G . |").single().bars[1]
        assertEquals(listOf(GridSegment.Hold(2), chord("G", beats = 2)), bar.segments)
    }

    @Test
    fun `a slash is a beat`() {
        assertEquals(listOf(chord("C", beats = 4)), grid("| C / / / |").single().bars.single().segments)
    }

    @Test
    fun `chords joined by a tilde are one segment naming both`() {
        assertEquals(listOf(chord("C", "G", beats = 2), chord("Am", beats = 2)), grid("| C~G . Am . |").single().bars.single().segments)
    }

    @Test
    fun `an empty bar holds the chord for the run's most common bar length`() {
        val bars = grid("| C . . | G . . | | D . . . |").single().bars
        assertEquals(listOf(GridSegment.Hold(3)), bars[2].segments)
        assertEquals(3, bars[2].beats)
    }

    @Test
    fun `a run with no bar to count makes an empty bar four beats long`() {
        assertEquals(4, commonBarLength(emptyList()))
    }

    @Test
    fun `a repeat sign keeps its bars and the bar after a two-bar repeat is covered by it`() {
        val bars = grid("| C . . . | % | G . . . | %% | |").single().bars
        assertEquals(listOf(GridSegment.Repeat(bars = 1, beats = 4)), bars[1].segments)
        assertEquals(listOf(GridSegment.Repeat(bars = 2, beats = 4)), bars[3].segments)
        assertEquals(emptyList(), bars[4].segments)
    }

    @Test
    fun `bar lines become the edges of the boxes next to them`() {
        val bars = grid("|: C . | D . :|: E . || F . |.").single().bars
        assertEquals(
            listOf(
                GridEdge.REPEAT to GridEdge.PLAIN,
                GridEdge.PLAIN to GridEdge.REPEAT,
                GridEdge.REPEAT to GridEdge.HEAVY,
                GridEdge.PLAIN to GridEdge.HEAVY,
            ),
            bars.map { it.opening to it.closing },
        )
    }

    @Test
    fun `a volta numbers the bar it opens`() {
        val bars = grid("| C . |1 D . :|2 E . |").single().bars
        assertEquals(listOf(null, "1.", "2."), bars.map { it.volta })
        assertEquals(GridEdge.REPEAT, bars[1].closing)
    }

    @Test
    fun `a margin label and a trailing count stay text`() {
        val line = grid("Intro | C . | G . | x2").single()
        assertEquals("Intro", line.margin)
        assertEquals("x2", line.trailing)
        assertEquals(2, line.bars.size)
    }

    @Test
    fun `a line without its outer bar lines still has its first and last bars`() {
        val line = grid("G | D | Em | C").single()
        assertEquals(listOf("G", "D", "Em", "C"), line.bars.map { (it.segments.single() as GridSegment.Chord).names.single() })
        assertNull(line.margin)
    }

    /** A layout in which every character of a label is one unit wide, a beat two, and the gaps are fixed. */
    private fun layout(vararg lines: String) = GridBarLayout(
        lines = grid(*lines),
        metrics = GridMetrics(em = 1f, textHeight = 1f),
        labelWidth = { it.length.toFloat() },
        textWidth = { it.length.toFloat() },
    )

    @Test
    fun `a two-beat bar is half as wide as a four-beat one`() {
        val placed = layout("| C . . . | D . |").placeLine(0, Float.POSITIVE_INFINITY)
        assertEquals(placed.bars[0].width, placed.bars[1].width * 2, absoluteTolerance = 0.001f)
    }

    @Test
    fun `a segment is at least as wide as its name`() {
        val segment = layout("| Cmaj7#11 |").placeLine(0, Float.POSITIVE_INFINITY).bars.single().segments.single()
        assertTrue(segment.width >= "Cmaj7#11".length)
    }

    @Test
    fun `bars line up in columns across lines of different lengths`() {
        val layout = layout("| C . . . | Cmaj7#11 . . . | D . . . |", "Intro | Am . . . | E . . . |")
        val first = layout.placeLine(0, Float.POSITIVE_INFINITY)
        val second = layout.placeLine(1, Float.POSITIVE_INFINITY)
        assertEquals(first.bars.take(2).map { it.x }, second.bars.map { it.x })
        assertEquals(first.bars.take(2).map { it.width }, second.bars.map { it.width })
        assertTrue(first.bars[0].x >= "Intro".length)
    }

    @Test
    fun `a wrapped run breaks every line after the same number of bars`() {
        val layout = layout("| C . . . | D . . . | E . . . | F . . . |", "| Am . . . | G . . . | Am . . . |")
        val maxWidth = layout.columnWidths.take(2).sum() + layout.metrics.barGap * 1.5f
        assertEquals(2, layout.barsPerRow(maxWidth))
        val first = layout.placeLine(0, maxWidth)
        val second = layout.placeLine(1, maxWidth)
        assertEquals(listOf(0, 0, 1, 1), first.bars.map { it.row })
        assertEquals(listOf(0, 0, 1), second.bars.map { it.row })
        assertEquals(first.bars[2].x, second.bars[2].x)
        assertEquals(2, second.rowTops.size)
    }

    @Test
    fun `a bar wider than the whole width is a row of its own and never cut`() {
        val layout = layout("| C . | Cmaj7#11~Dm7b5 . |")
        val placed = layout.placeLine(0, 4f)
        assertEquals(listOf(0, 1), placed.bars.map { it.row })
        assertTrue(placed.bars[1].width > 4f)
    }

    @Test
    fun `a trailing count follows the last bar, or starts a row where it does not fit`() {
        val layout = layout("| C . | D . | x2")
        val unbroken = layout.placeLine(0, Float.POSITIVE_INFINITY)
        assertEquals(0, unbroken.trailing?.row)
        assertTrue(unbroken.trailing!!.x > unbroken.bars.last().x + unbroken.bars.last().width)
        val broken = layout.placeLine(0, unbroken.bars.last().x + unbroken.bars.last().width + 0.5f)
        assertEquals(1, broken.trailing?.row)
    }

    @Test
    fun `a row holding a volta leaves room above it for the bracket`() {
        val layout = layout("| C . |1 D . :|")
        val plain = layout("| C . | D . |").placeLine(0, Float.POSITIVE_INFINITY).rowTops.single()
        assertEquals(plain + layout.metrics.voltaHeight, layout.placeLine(0, Float.POSITIVE_INFINITY).rowTops.single())
    }
}
