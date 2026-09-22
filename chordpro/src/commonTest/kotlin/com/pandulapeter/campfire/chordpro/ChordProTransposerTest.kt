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
    fun `a German-notated song is transposed in its own notation`() {
        val text = "{key: Dm}\n[Dm]a [B]b [C/H]c [A7]d"
        val up = ChordProTransposer.transposeText(text, 1)

        assertEquals("{key: Ebm}\n[Ebm]a [H]b [Db/C]c [B7]d", up)
        assertEquals(text, ChordProTransposer.transposeText(up, -1))
    }

    @Test
    fun `a German-notated song that is left with no H spells its B flat out`() {
        assertEquals("[C7]a [F]b [Bb]c", ChordProTransposer.transposeText("[H7]a [E]b [A]c", 1, preferFlats = true))
    }

    @Test
    fun `the root note and the bass note are both transposed`() {
        assertEquals("Bm7/A", ChordProTransposer.transposeChord("Am7/G", 2, preferFlats = false))
    }

    @Test
    fun `a lowercase bass note is transposed and stays lowercase`() {
        assertEquals("E/g#", ChordProTransposer.transposeChord("D/f#", 2, preferFlats = false))
        assertEquals("[E/g#] [e/g#]", ChordProTransposer.transposeText("[D/f#] [d/f#]", 2, preferFlats = false))
    }

    @Test
    fun `a lowercase bass note round-trips`() {
        val text = "[D/f#]a [d/f#]b [A/c#]c [Am7/G]d"

        assertEquals(text, ChordProTransposer.transposeText(ChordProTransposer.transposeText(text, 2, preferFlats = false), -2, preferFlats = false))
    }

    @Test
    fun `a grid cell with a lowercase bass note is transposed`() {
        assertEquals(
            "{start_of_grid}\n| E/g# | A |\n{end_of_grid}",
            ChordProTransposer.transposeText("{start_of_grid}\n| D/f# | G |\n{end_of_grid}", 2, preferFlats = false),
        )
    }

    @Test
    fun `a lowercase bass note is transposed on the model`() {
        assertEquals(listOf("E/g#", "Em/g#"), ChordProTransposer.transpose(ChordProParser.parse("[D/f#]a [d/f#]b"), 2, preferFlats = false).chordNames())
    }

    @Test
    fun `a capitalised bass note stays capitalised`() {
        assertEquals("[Bm7/A]", ChordProTransposer.transposeText("[Am7/G]", 2, preferFlats = false))
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
    fun `without a key the first chord stands in for it`() {
        assertTrue(ChordProTransposer.prefersFlats(ChordProParser.parse("[G]a [C]b [D]c"), 1))
        assertFalse(ChordProTransposer.prefersFlats(ChordProParser.parse("[Eb]a [Bb]b [Cm]c"), 1))
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
    fun `a tab inside a section moves its frets while the lyrics around it move their chords`() {
        val song = ChordProParser.parse(
            """
            {start_of_verse: Solo}
            [Am]over the solo
            {start_of_tab}
            e|---0---|
            {end_of_tab}
            back to [C]lyrics
            {end_of_verse}
            """.trimIndent()
        ).let { ChordProTransposer.transpose(it, semitones = 2) }

        val lines = (song.blocks.single() as ChordProBlock.Section).lines
        assertEquals(listOf("Bm", "D"), song.chordNames())
        assertEquals("e|---2---|", (lines[1] as ChordProLine.Tab).text)
    }

    @Test
    fun `the key keeps the spelling of its directive and a chord the spaces in its brackets`() {
        assertEquals("{KEY:A}\n[ A ]la [Bm ]la", ChordProTransposer.transposeText("{KEY:G}\n[ G ]la [Am ]la", 2))
        assertEquals("  { key : A }  ", ChordProTransposer.transposeText("  { key : G }  ", 2))
        assertEquals("{key: }", ChordProTransposer.transposeText("{key: }", 2))
        assertEquals(6, ChordProTransposer.transposedOffset("{KEY:G}", "{KEY:A}", 6))
    }

    @Test
    fun `lowercase minors are transposed and keep their spelling in the text`() {
        assertEquals("[C#]a [c#]b [h]c", ChordProTransposer.transposeText("[H]a [h]b [a]c", 2, preferFlats = false))
        assertEquals("[D]a [b]b [A]c", ChordProTransposer.transposeText("[C]a [a]b [G]c", 2, preferFlats = false))
        assertEquals("[H]a [h]b [a]c", ChordProTransposer.transposeText("[C#]a [c#]b [h]c", -2, preferFlats = false))
        assertEquals("[C]a [a]b [G]c", ChordProTransposer.transposeText("[D]a [b]b [A]c", -2, preferFlats = false))
    }

    @Test
    fun `lowercase minors are transposed on the model`() {
        assertEquals(listOf("D", "Bm"), ChordProTransposer.transpose(ChordProParser.parse("[C]a [a]b"), 2, preferFlats = false).chordNames())
    }

    @Test
    fun `a lowercase word in brackets is not a chord`() {
        assertEquals("[fine]", ChordProTransposer.transposeText("[fine]", 2))
    }

    @Test
    fun `a section label in brackets is not transposed`() {
        val text = "[Intro] [Break] [Chorus 2x] [Bass] [Ebony]"

        assertEquals(text, ChordProTransposer.transposeText(text, 3))
        assertEquals(ChordProParser.parse(text), ChordProTransposer.transpose(ChordProParser.parse(text), 3))
    }

    @Test
    fun `a forced spelling leaves a label alone`() {
        assertEquals("[Ebony] tower", ChordProTransposer.transposeText("[Ebony] tower", 0, preferFlats = false))
    }

    @Test
    fun `a label keeps the spaces inside its brackets`() {
        assertEquals("[ Break ]la", ChordProTransposer.transposeText("[ Break ]la", 2))
    }

    @Test
    fun `a grid cell that is not a chord is left alone`() {
        assertEquals(
            "{start_of_grid}\n| Bm . A | Coda |\n{end_of_grid}",
            ChordProTransposer.transposeText("{start_of_grid}\n| Am . G | Coda |\n{end_of_grid}", 2, preferFlats = false),
        )
    }

    @Test
    fun `a key that is not a chord name is left alone`() {
        assertEquals("{key: Dm (capo 2)}", ChordProTransposer.transposeText("{key: Dm (capo 2)}", 2))
        assertEquals("{key: Em}", ChordProTransposer.transposeText("{key: Dm}", 2))
    }

    @Test
    fun `a real chord is still transposed`() {
        listOf(
            "C", "Am7/G", "C#m7b5", "Bsus4", "(Em)", "F#m", "Gadd9", "Cmaj7", "Am(no3)", "D7sus4/A", "Bb/D", "H7", "A-", "D♭",
            "G♯m", "D/f#",
        ).forEach { name ->
            assertTrue(ChordProTransposer.transposeChord(name, 1, preferFlats = false) != name, name)
        }
    }

    @Test
    fun `a caret keeps its place on a line of labels that did not change`() {
        val before = "[Break]la\n[C]lo"
        val (after, inside) = transposed(before, 1, 3)

        assertEquals("[Break]la\n[C#]lo", after)
        assertEquals(3, inside)
        assertEquals(after.length, transposed(before, 1, before.length).second)
    }

    @Test
    fun `a tab chord row still moves and its labels do not`() {
        val text = "{start_of_tab}\nRiff 1\nAm      G\ne|-0-----3-|\n{end_of_tab}"

        assertEquals(
            "{start_of_tab}\nRiff 1\nBm      A\ne|-2-----5-|\n{end_of_tab}",
            ChordProTransposer.transposeText(text, 2, preferFlats = false),
        )
    }

    @Test
    fun `an abc block is left alone by the transposition`() {
        val text = "{start_of_abc}\n[CEG]2\n{end_of_abc}\n[C]la"

        assertEquals("{start_of_abc}\n[CEG]2\n{end_of_abc}\n[D]la", ChordProTransposer.transposeText(text, 2))
        assertEquals(ChordProParser.parse("{start_of_abc}\n[CEG]2\n{end_of_abc}\n[D]la"), ChordProTransposer.transpose(ChordProParser.parse(text), 2))
    }

    @Test
    fun `a grid keeps its margin labels and moves every chord of a cell`() {
        val text = "{start_of_grid}\nA    || G7 . | C~A . |\nCoda | D7 |.\n{end_of_grid}"

        assertEquals(
            "{start_of_grid}\nA    || A7 . | D~B . |\nCoda | E7 |.\n{end_of_grid}",
            ChordProTransposer.transposeText(text, 2, preferFlats = false),
        )
        val tokens = ChordProTransposer.transpose(ChordProParser.parse(text), 2, preferFlats = false).blocks
            .filterIsInstance<ChordProBlock.Section>().flatMap { it.lines }
            .filterIsInstance<ChordProLine.Grid>().flatMap { it.tokens }
        assertTrue(GridToken.Chord("D~B") in tokens)
        assertTrue(GridToken.Text("Coda") in tokens)
    }

    @Test
    fun `a modulation does not decide the spelling of the whole song`() {
        val song = ChordProTransposer.transpose(ChordProParser.parse("{key: F}\n[C]a\n{key: A}\n[E]b"), 1)

        assertEquals("F#", song.metadata.key)
        assertEquals(
            listOf("C#", "F"),
            song.blocks.filterIsInstance<ChordProBlock.Section>().flatMap { it.lines }
                .filterIsInstance<ChordProLine.Lyrics>().flatMap { line -> line.chords.map { it.name } },
        )
    }

    @Test
    fun `a key written as meta is transposed with the chords`() {
        assertEquals("{meta: key A}\n[A]a", ChordProTransposer.transposeText("{meta: key G}\n[G]a", 2))
        assertTrue(ChordProTransposer.prefersFlats(ChordProParser.parse("{meta: key F}\n[C]a"), 0))
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
    fun `a tab with a blank line in it is one fingerboard in the model as in the text`() {
        val text = "{sov}\n{sot}\ne|--0--|\n\ne|--20--|\n{eot}\n{eov}"

        assertEquals(
            ChordProTransposer.transpose(ChordProParser.parse(text), -2, preferFlats = false),
            ChordProParser.parse(ChordProTransposer.transposeText(text, -2, preferFlats = false)),
        )
        assertEquals(
            listOf("e|--0--|", "e|--20--|"),
            ChordProTransposer.transpose(ChordProParser.parse(text), -2, preferFlats = false).tabLines(),
        )
    }

    @Test
    fun `a comment inside a tab moves the frets on both sides of it in the model as in the text`() {
        val text = "{sot}\ne|---0---2---|\n{comment: Repeat x2}\ne|---3---5---|\n{eot}"

        assertEquals("{sot}\ne|---2---4---|\n{comment: Repeat x2}\ne|---5---7---|\n{eot}", ChordProTransposer.transposeText(text, 2))
        assertEquals(listOf("e|---2---4---|", "e|---5---7---|"), ChordProTransposer.transpose(ChordProParser.parse(text), 2).tabLines())
    }

    @Test
    fun `the two halves of a tab cut by a comment are fingerboards of their own in the model as in the text`() {
        val text = "{sot}\ne|--0--|\n{c: Higher}\ne|--20--|\n{eot}"

        assertEquals(
            ChordProTransposer.transpose(ChordProParser.parse(text), -2, preferFlats = false),
            ChordProParser.parse(ChordProTransposer.transposeText(text, -2, preferFlats = false)),
        )
        assertEquals("{sot}\ne|--10-|\n{c: Higher}\ne|--18--|\n{eot}", ChordProTransposer.transposeText(text, -2, preferFlats = false))
    }

    @Test
    fun `the two halves of a tab cut by a highlight are fingerboards of their own in the model as in the text`() {
        val text = "{sot}\ne|--0--|\n{highlight: Higher}\ne|--20--|\n{eot}"

        assertEquals(
            ChordProTransposer.transpose(ChordProParser.parse(text), -2, preferFlats = false),
            ChordProParser.parse(ChordProTransposer.transposeText(text, -2, preferFlats = false)),
        )
        assertEquals("{sot}\ne|--10-|\n{highlight: Higher}\ne|--18--|\n{eot}", ChordProTransposer.transposeText(text, -2, preferFlats = false))
    }

    @Test
    fun `a comment inside a grid leaves the rest of it a grid to transpose`() {
        val song = ChordProTransposer.transpose(ChordProParser.parse("{sog}\n| Am . |\n{c: x}\n| C . |\n{eog}"), 2)

        assertEquals(listOf("Bm", "D"), song.chordNames())
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

    @Test
    fun `transposing text keeps the trailing line break exactly where the file had one`() {
        assertEquals("[Bbm]la\n", ChordProTransposer.transposeText("[Am]la\n", 1))
        assertEquals("[Bbm]la", ChordProTransposer.transposeText("[Am]la", 1))
    }

    @Test
    fun `transposing text keeps the line endings of the file`() {
        assertEquals("[C#]la\r\n[G#]lo\r\n", ChordProTransposer.transposeText("[C]la\r\n[G]lo\r\n", 1, preferFlats = false))
    }

    @Test
    fun `a caret after a lyric keeps its lyric when every chord above it grows`() {
        val before = "[C]Amazing [F]grace, how [C]sweet the sound\nThat [C]saved a [G]wretch like [C]me\n[C]I once was [F]lost, but [C]now am found"
        val (after, mapped) = transposed(before, 1, before.length)

        assertEquals("found", after.substring(mapped - 5, mapped))
    }

    @Test
    fun `a caret between two chords keeps its place in the lyrics`() {
        val before = "[C]Hello [G]world"
        listOf(1, -1).forEach { semitones ->
            val (after, mapped) = transposed(before, semitones, before.indexOf("lo"))

            assertEquals("Hel", after.substring(mapped - 3, mapped), "$semitones")
            assertTrue(after.substring(mapped).startsWith("lo ["), "$semitones")
        }
    }

    @Test
    fun `a caret after a chord name stays after the whole transposed name`() {
        val (after, mapped) = transposed("[C]x", 1, 2)

        assertEquals("[C#", after.substring(0, mapped))
    }

    @Test
    fun `a caret inside a chord name stays inside the brackets`() {
        val (after, mapped) = transposed("[F#m7]x", 1, 2)

        assertTrue(mapped > after.indexOf('[') && mapped <= after.indexOf(']'))
    }

    @Test
    fun `the start and the end of the text map to the start and the end`() {
        val before = "[C]la [G]lo"

        assertEquals(0, transposed(before, 1, 0).second)
        val (after, mapped) = transposed(before, 1, before.length)
        assertEquals(after.length, mapped)
    }

    @Test
    fun `a caret after a key that grows stays after the new key`() {
        val (after, mapped) = transposed("{key: C}\n[C]a", 1, 7)

        assertEquals(after.indexOf('}'), mapped)
    }

    @Test
    fun `a caret after a tab fret that grew keeps its distance from the line end`() {
        val before = "{start_of_tab}\ne|--9--|--0--|\n{end_of_tab}"
        val lineEnd = before.lastIndexOf('\n')
        val (after, mapped) = transposed(before, 2, lineEnd - 2)

        assertEquals(2, after.lastIndexOf('\n') - mapped)
    }

    @Test
    fun `a caret at the end of a CRLF line stays at the end of that line`() {
        val before = "[C]la\r\n[G]lo\r\n[C]end"
        val (after, mapped) = transposed(before, 1, before.indexOf("\r\n", before.indexOf("lo")))

        assertEquals(after.indexOf("\r\n", after.indexOf("lo")), mapped)
    }

    @Test
    fun `a caret at the start of a line keeps it where joinLines unified the line endings`() {
        val before = "[C]a\n[G]b\r\n[C]c"
        val (after, mapped) = transposed(before, 1, before.lastIndexOf('['))

        assertEquals("[C#]a\r\n[G#]b\r\n[C#]c", after)
        assertEquals(after.lastIndexOf('['), mapped)
    }

    @Test
    fun `an unchanged text maps every offset to itself and an offset out of range is clamped`() {
        assertEquals(3, ChordProTransposer.transposedOffset("[C]la", "[C]la", 3))
        assertEquals(0, ChordProTransposer.transposedOffset("[C]la", "[C#]la", -4))
        assertEquals("[C#]la".length, ChordProTransposer.transposedOffset("[C]la", "[C#]la", 100))
    }

    /** The transposition of [before] and where [offset] maps to in it, always against the transposer's own output. */
    private fun transposed(before: String, semitones: Int, offset: Int): Pair<String, Int> {
        val after = ChordProTransposer.transposeText(before, semitones, preferFlats = false)
        return after to ChordProTransposer.transposedOffset(before, after, offset)
    }
}
