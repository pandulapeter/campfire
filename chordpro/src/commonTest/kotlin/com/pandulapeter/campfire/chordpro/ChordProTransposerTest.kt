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

import com.pandulapeter.campfire.chordpro.model.ChordProBlock
import com.pandulapeter.campfire.chordpro.model.ChordProLine
import com.pandulapeter.campfire.chordpro.model.ChordProSong
import com.pandulapeter.campfire.chordpro.model.GridToken
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ChordProTransposerTest {

    @Test
    fun `the root note and the bass note are both transposed`() {
        assertEquals("Bm7/A", ChordProTransposer.transposeChord("Am7/G", 2, preferFlats = false))
    }

    @Test
    fun `accidentals follow the preferred spelling`() {
        assertEquals("B", ChordProTransposer.transposeChord("Bb", 1, preferFlats = true))
        assertEquals("C", ChordProTransposer.transposeChord("C#", -1, preferFlats = false))
        assertEquals("Db", ChordProTransposer.transposeChord("C", 1, preferFlats = true))
        assertEquals("C#", ChordProTransposer.transposeChord("C", 1, preferFlats = false))
    }

    @Test
    fun `german notation is understood`() {
        assertEquals("C", ChordProTransposer.transposeChord("H", 1, preferFlats = false))
    }

    @Test
    fun `names that are not chords are returned unchanged`() {
        assertEquals("N.C.", ChordProTransposer.transposeChord("N.C.", 2, preferFlats = false))
    }

    @Test
    fun `flats are preferred when the key transposes into a flat key`() {
        val song = ChordProParser.parse("{key: E}\n\n[E]a [A]b")

        assertTrue(ChordProTransposer.prefersFlats(song, 1)) // E -> F
        assertFalse(ChordProTransposer.prefersFlats(song, 3)) // E -> G
        assertTrue(ChordProTransposer.prefersFlats(ChordProParser.parse("{key: Am}\n\n[Am]a"), 3)) // Am -> Cm
    }

    @Test
    fun `without a key the accidentals of the chords decide`() {
        assertTrue(ChordProTransposer.prefersFlats(ChordProParser.parse("[Bb]a [Eb]b [C]c"), 1))
        assertFalse(ChordProTransposer.prefersFlats(ChordProParser.parse("[F#]a [C#]b [C]c"), 1))
    }

    @Test
    fun `transposing by zero returns the input untouched`() {
        val song = ChordProParser.parse("{key: E}\n\n[E]a")
        val text = "{key: E}\n\n[E]a"

        assertSame(song, ChordProTransposer.transpose(song, 0))
        assertSame(text, ChordProTransposer.transposeText(text, 0))
    }

    @Test
    fun `a forced spelling overrides the one the song asks for`() {
        val song = ChordProParser.parse("{key: E}\n\n[E]a [A]b") // E -> F, a flat key.

        assertEquals(listOf("F", "Bb"), ChordProTransposer.transpose(song, 1).chordNames())
        assertEquals(listOf("F", "Bb"), ChordProTransposer.transpose(song, 1, preferFlats = true).chordNames())
        assertEquals(listOf("F", "A#"), ChordProTransposer.transpose(song, 1, preferFlats = false).chordNames())
    }

    @Test
    fun `a forced spelling respells a song that is not transposed at all`() {
        val song = ChordProParser.parse("{key: Eb}\n\n[Eb]a [Bb]b")

        assertSame(song, ChordProTransposer.transpose(song, 0))
        assertEquals(listOf("Eb", "Bb"), ChordProTransposer.transpose(song, 0, preferFlats = true).chordNames())
        assertEquals(listOf("D#", "A#"), ChordProTransposer.transpose(song, 0, preferFlats = false).chordNames())
        assertEquals("D#", ChordProTransposer.transpose(song, 0, preferFlats = false).metadata.key)
    }

    @Test
    fun `a forced spelling rewrites the text of a song that is not transposed at all`() {
        val text = "{key: Eb}\n\n[Eb]a [Bb/Db]b\n\n{sog}\n| Eb . | Bb . |\n{eog}"

        assertSame(text, ChordProTransposer.transposeText(text, 0))
        assertEquals(
            "{key: D#}\n\n[D#]a [A#/C#]b\n\n{sog}\n| D# . | A# . |\n{eog}",
            ChordProTransposer.transposeText(text, 0, preferFlats = false),
        )
    }

    @Test
    fun `transposing by an octave keeps every chord name`() {
        val song = ChordProParser.parse("{key: Am}\n\n[Am]a [F]b [C]c [G]d\n\n{sog}\n| Am . | G . |\n{eog}")

        assertEquals(song.chordNames(), ChordProTransposer.transpose(song, 12).chordNames())
    }

    @Test
    fun `transposing the model moves chords grid chords and the key but not annotations`() {
        val song = ChordProTransposer.transpose(ChordProParser.parse("{key: Am}\n\n[Am]a [*hold]b\n\n{sog}\n| Am . |\n{eog}"), 2)

        assertEquals("Bm", song.metadata.key)
        assertEquals(listOf("Bm", "Bm"), song.chordNames())
        assertEquals(listOf("hold"), song.annotationNames())
    }

    @Test
    fun `transposing text leaves comments and annotations alone and updates the key`() {
        val text = """
            # a note with [Am] inside
            {key: E}
            {comment: Not a chord}

            {start_of_tab}
            e|--[Am]--|
            {end_of_tab}

            [C]one [*hold]two

            {start_of_grid}
            | C . . . | (twice)
            {end_of_grid}
        """.trimIndent()

        assertEquals(
            """
            # a note with [Am] inside
            {key: F}
            {comment: Not a chord}

            {start_of_tab}
            e|--[Am]--|
            {end_of_tab}

            [Db]one [*hold]two

            {start_of_grid}
            | Db . . . | (twice)
            {end_of_grid}
            """.trimIndent(),
            ChordProTransposer.transposeText(text, 1),
        )
    }

    @Test
    fun `transposing text moves the frets of a tab and the chord names above it`() {
        val text = """
            {key: E}

            {start_of_tab}
            Riff 1 (play twice)
            Tuning: E A D G B E
              E        A
            e|--0--3--5--|
            B|--1--x--12-|
            {end_of_tab}
        """.trimIndent()

        assertEquals(
            """
            {key: F}

            {start_of_tab}
            Riff 1 (play twice)
            Tuning: E A D G B E
              F        Bb
            e|--1--4--6--|
            B|--2--x--13-|
            {end_of_tab}
            """.trimIndent(),
            ChordProTransposer.transposeText(text, 1),
        )
    }

    @Test
    fun `a fret number of a different width keeps the columns of the tab`() {
        val widening = "{sot}\ne|--0--3--5--|\ne|--10-12----|\n{eot}"
        val narrowing = "{sot}\ne|--3--5--|\ne|--10-12----|\n{eot}"

        assertEquals("{sot}\ne|--9--12-14-|\ne|--19-21----|\n{eot}", ChordProTransposer.transposeText(widening, 9))
        assertEquals("{sot}\ne|--1--3--|\ne|--8--10----|\n{eot}", ChordProTransposer.transposeText(narrowing, -2))
    }

    @Test
    fun `a tab that would fall off the fingerboard moves by an octave instead`() {
        assertEquals("{sot}\ne|--10-13-15-|\n{eot}", ChordProTransposer.transposeText("{sot}\ne|--0--3--5--|\n{eot}", -2))
    }

    @Test
    fun `a tab that fits in no octave is left alone`() {
        val text = "{sot}\ne|--0--24-|\n{eot}"

        assertEquals(text, ChordProTransposer.transposeText(text, 2))
    }

    @Test
    fun `transposing the model moves the frets of a tab section`() {
        val song = ChordProTransposer.transpose(ChordProParser.parse("{key: Am}\n\n{sot}\n  Am\ne|--0--3--|\n{eot}"), 2)

        assertEquals(listOf("  Bm", "e|--2--5--|"), song.tabLines())
    }

    private fun ChordProSong.lines() = blocks.filterIsInstance<ChordProBlock.Section>().flatMap { it.lines }

    private fun ChordProSong.chordNames() = lines().flatMap { line ->
        when (line) {
            is ChordProLine.Lyrics -> line.chords.filter { !it.isAnnotation }.map { it.name }
            is ChordProLine.Grid -> line.tokens.filterIsInstance<GridToken.Chord>().map { it.name }
            else -> emptyList()
        }
    }

    private fun ChordProSong.tabLines() = lines().filterIsInstance<ChordProLine.Tab>().map { it.text }

    private fun ChordProSong.annotationNames() = lines()
        .filterIsInstance<ChordProLine.Lyrics>()
        .flatMap { line -> line.chords.filter { it.isAnnotation }.map { it.name } }
}
