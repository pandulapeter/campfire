/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails

import com.pandulapeter.campfire.chordpro.ChordProParser
import com.pandulapeter.campfire.chordpro.model.ChordProBlock
import com.pandulapeter.campfire.chordpro.model.ChordProLine
import com.pandulapeter.campfire.chordpro.model.GridToken
import kotlin.test.Test
import kotlin.test.assertEquals

class GridColumnsTest {

    private fun GridToken.text() = when (this) {
        is GridToken.Bar -> text
        is GridToken.Chord -> name
        GridToken.Beat -> "."
        is GridToken.Repeat -> text
        is GridToken.Text -> text
    }

    /** The tokens of each line of a grid environment holding [lines], as the parser reads them. */
    private fun gridTokens(vararg lines: String) = ChordProParser.parse(lines.joinToString("\n", prefix = "{start_of_grid}\n", postfix = "\n{end_of_grid}"))
        .blocks.filterIsInstance<ChordProBlock.Section>()
        .flatMap { it.lines }
        .filterIsInstance<ChordProLine.Grid>()
        .map { it.tokens }

    /** Each line of the run as the text its aligned bars draw, bars joined as they are drawn side by side. */
    private fun aligned(vararg lines: String) = gridTokens(*lines)
        .alignedGridBars { it.text() }
        .map { bars -> bars.joinToString("") { bar -> bar.joinToString("") { it.text } } }

    @Test
    fun `the bar lines of a run stand under each other whatever the chords are`() {
        assertEquals(
            listOf(
                "|: Am . . | C  . . | D  . .  | F . . |",
                "|  Am . . | E7 . . | Am     :| x2",
            ),
            aligned(
                "|: Am . . | C . . | D . . | F . . |",
                "| Am . . | E7 . . | Am :| x2",
            ),
        )
    }

    @Test
    fun `a closing repeat ends where the bar lines above it do`() {
        assertEquals(
            listOf(
                "|: Am | C  | D  | F   |",
                "|  Am | E7 | Am | Am :| x2",
            ),
            aligned(
                "|: Am | C | D | F |",
                "| Am | E7 | Am | Am :| x2",
            ),
        )
    }

    @Test
    fun `a margin label pushes the first bar line of every line to the same column`() {
        assertEquals(
            listOf(
                "Coda | G . |",
                "     | D . |",
            ),
            aligned(
                "Coda | G . |",
                "| D . |",
            ),
        )
    }

    @Test
    fun `the note after a line's last bar line widens no bar of a longer line`() {
        assertEquals(
            listOf(
                "| Am | C | D | F | G |",
                "| Am | C | D | F | x2 (fade out)",
            ),
            aligned(
                "| Am | C | D | F | G |",
                "| Am | C | D | F | x2 (fade out)",
            ),
        )
    }

    @Test
    fun `a line with a margin label and a note after its last bar line lines up with the bars under it`() {
        assertEquals(
            listOf(
                "Coda  | G  . | x2",
                "Intro | Am . | C . |",
            ),
            aligned(
                "Coda | G . | x2",
                "Intro | Am . | C . |",
            ),
        )
    }

    @Test
    fun `a line with no bar line is aligned as one bar`() {
        assertEquals(
            listOf(
                "| Am   | C |",
                "  N.C.",
            ),
            aligned(
                "| Am | C |",
                "N.C.",
            ),
        )
    }

    @Test
    fun `a combining accent in a margin label takes up no column of its own`() {
        assertEquals(
            listOf(
                "Refre\u0301n | Am |",
                "Chorus | C  |",
            ),
            aligned(
                "Refre\u0301n | Am |",
                "Chorus | C |",
            ),
        )
    }

    @Test
    fun `a line on its own is drawn as written, with one space between its tokens`() {
        assertEquals(listOf("| Am . | C~G % |"), aligned("|   Am  .  |  C~G  %   |"))
    }

    @Test
    fun `a bar ends with the space before the next one, and a line ends with its last token`() {
        val bars = gridTokens("| Am | C |").alignedGridBars { it.text() }.single()
        assertEquals(listOf("| Am | ", "C |"), bars.map { bar -> bar.joinToString("") { it.text } })
    }

    @Test
    fun `a grid line is cut into bars ending on the bar line that closes them`() {
        assertEquals(
            listOf(listOf("|:", "Am", "|"), listOf("C", ":|"), listOf("x2")),
            gridTokens("|: Am | C :| x2").single().bars().map { bar -> bar.map { it.text() } },
        )
    }
}
