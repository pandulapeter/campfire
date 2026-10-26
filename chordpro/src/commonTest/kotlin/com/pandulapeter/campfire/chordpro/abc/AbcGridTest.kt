/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.abc

import com.pandulapeter.campfire.chordpro.ChordProParser
import com.pandulapeter.campfire.chordpro.model.ChordProBlock
import com.pandulapeter.campfire.chordpro.model.ChordProLine
import com.pandulapeter.campfire.chordpro.model.GridToken
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class AbcGridTest {

    /** The grid rows the converted tune parses into, each token as it is spelled, and whether every one is a grid token. */
    private fun gridOf(body: String): List<String> {
        val song = AbcToChordPro.convert("X:1\nT:Tune\nM:4/4\nK:G\n$body")
        assertTrue(song.hasChords)
        val rows = ChordProParser.parse(song.text).blocks
            .filterIsInstance<ChordProBlock.Section>()
            .flatMap { it.lines }
            .filterIsInstance<ChordProLine.Grid>()
        rows.forEach { row -> assertTrue(row.tokens.none { it is GridToken.Text }, "no token of $row is a margin text") }
        return rows.map { row -> row.tokens.joinToString(" ", transform = ::spelling) }
    }

    private fun spelling(token: GridToken) = when (token) {
        is GridToken.Bar -> token.text
        is GridToken.Chord -> token.name
        GridToken.Beat -> "."
        is GridToken.Repeat -> token.text
        is GridToken.Text -> token.text
    }

    @Test
    fun `each line of music is a grid row and each bar its chords`() {
        val grid = gridOf("\"G\"GABc \"D\"dedB|\"Em\"e2 \"C\"c2 \"D\"d4|\n\"G\"GABc dedB|\"C\"c2 \"D\"d2 \"G\"g4|]")

        assertEquals(listOf("| G D | Em C D |", "| G | C D G |."), grid)
    }

    @Test
    fun `repeats and voltas are written in grid syntax`() {
        val grid = gridOf("|:\"G\"GABc|\"D\"dedB|[1\"G\"g4:|[2\"D\"d4||\n[|\"Em\"e4::\"C\"c4|\"D\"d4|2\"G\"g4|]")

        assertEquals(listOf("|: G | D |1 G :|2 D ||", "|| Em :|: C | D |2 G |."), grid)
    }

    @Test
    fun `a bar with no chord carries the chord before it on`() {
        assertEquals(listOf("| G | % | D | % |"), gridOf("\"G\"GABc|dedB|\"D\"d4|D4|"))
    }

    @Test
    fun `bars before the first chord are beats, and a chordless pickup is left out`() {
        assertEquals(listOf("| . | G |"), gridOf("D2|GABc|\"G\"dedB|"))
    }

    @Test
    fun `annotations, alternative chords, and text are not chords`() {
        assertEquals(listOf("| G | D |"), gridOf("\"^Fine\"\"G\"GA\"(Em)\"Bc|\"_rit.\"\"D\"de\"<here\"dB|\"Slowly\"|"))
    }

    @Test
    fun `inline fields, decorations, grace notes, and chords of notes are skipped`() {
        assertEquals(listOf("| G | D | A |"), gridOf("\"G\"!trill!G{ga}A[GBd]c|\"D\"dedB|[K:A][M:3/4]\"A\"ABc|"))
    }

    @Test
    fun `a line continued with a backslash is one row, and one ending mid-bar breaks after that bar`() {
        assertEquals(listOf("| G | D |"), gridOf("\"G\"GABc|\\\n\"D\"dedB|"))
        assertEquals(listOf("| G |", "| D |"), gridOf("\"G\"GA\nBc|\"D\"dedB|"))
    }

    @Test
    fun `comments and lyrics lines in the body are not music`() {
        assertEquals(listOf("| G | D |"), gridOf("\"G\"GABc|\"D\"dedB| % the A part\nw:la la la la\n%%vskip 10"))
    }

    @Test
    fun `a closing bar line alone on the last line closes the row before it`() {
        assertEquals(listOf("| G | D |."), gridOf("\"G\"GABc|\"D\"dedB|\n|]"))
    }

    @Test
    fun `a long line of unclosed grace note braces is read in linear time`() {
        val start = TimeSource.Monotonic.markNow()

        val grid = gridOf("\"G\"G" + "{ ".repeat(200_000) + "|")

        assertTrue(start.elapsedNow() < 2.seconds, start.elapsedNow().toString())
        assertEquals(listOf("| G |"), grid)
    }

    @Test
    fun `only the first voice of a tune is read, named on a line of its own or inline`() {
        assertEquals(
            listOf("| G | C D |", "| G | D |"),
            gridOf("V:1\n\"G\"G2B2 d2B2|\"C\"c2e2 \"D\"d4|\nV:2 clef=bass\nG,,4 D,4|C,4 D,4|\n[V:1]\"G\"G4|\"D\"D4|\n[V:2]G,4|D,4|"),
        )
    }
}
