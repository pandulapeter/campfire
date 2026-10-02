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
import kotlin.test.assertTrue

class ChordProNotationTest {

    @Test
    fun `a key spelled out in words is written in German notation`() {
        assertEquals("H major", ChordProNotation.toNotation(ChordProParser.parse("{key: B major}\n[B]a"), ChordNotation.GERMAN).metadata.key)
        assertEquals("B major", ChordProParser.parse("{key: H major}\n[H]a").metadata.key)
        assertEquals(ChordProParser.parse("{key: H major}\n[H]a").metadata.key, ChordProParser.summarize("{key: H major}\n[H]a").metadata.key)
    }

    @Test
    fun `accidental signs are folded wherever they stand in a name`() {
        assertEquals("Bbm7b5/C#", ChordProNotation.withAsciiAccidentals("B♭m7♭5/C♯"))
    }

    @Test
    fun `a name written in German notation is read into the app's own`() {
        assertEquals("B", ChordProNotation.fromGerman("H"))
        assertEquals("Bm7", ChordProNotation.fromGerman("Hm7"))
        assertEquals("Bb", ChordProNotation.fromGerman("B"))
        assertEquals("Bb7", ChordProNotation.fromGerman("B7"))
        assertEquals("F/Bb", ChordProNotation.fromGerman("F/B"))
        assertEquals("Hello", ChordProNotation.fromGerman("Hello"))
    }

    @Test
    fun `a song says it is German notated with an H chord anywhere`() {
        assertTrue(ChordProNotation.isGermanNotated(ChordProParser.parseAsWritten("[H7]la [B]la")))
        assertTrue(ChordProNotation.isGermanNotated(ChordProParser.parseAsWritten("{key: B}\n[C/H]la")))
        assertFalse(ChordProNotation.isGermanNotated(ChordProParser.parseAsWritten("[Hello]la [B]la")))
        assertTrue(ChordProNotation.isGermanNotated(ChordProParser.parseAsWritten("{sog}\n| C~H |\n{eog}")))
    }

    @Test
    fun `a lowercase h marks a song as German`() {
        assertTrue(ChordProNotation.isGermanNotated(ChordProParser.parseAsWritten("[D]a [h]b")))
    }

    @Test
    fun `a lowercase bass note is read and written in German notation`() {
        assertEquals("C/h", ChordProNotation.toGerman("C/b"))
        assertEquals("C/b", ChordProNotation.toGerman("C/bb"))
        assertEquals("C/b", ChordProNotation.fromGerman("C/h"))
        assertEquals("C/bb", ChordProNotation.fromGerman("C/b"))
        assertTrue(ChordProNotation.isGermanName("C/h"))
        assertTrue(ChordProNotation.isGermanNotated(ChordProParser.parseAsWritten("[D]a [C/h]b")))
    }

    @Test
    fun `lowercase minors are read spelled out`() {
        assertEquals(listOf("B", "Bm", "Am", "Bbm"), ChordProParser.parse("[H]a [h]b [a]c [b]d").chordNames())
        assertEquals(listOf("F", "Dm"), ChordProParser.parse("[F]a [d]b").chordNames())
    }

    @Test
    fun `every chord of a grid cell is respelled`() {
        assertEquals(listOf("H~B"), ChordProNotation.toNotation(ChordProParser.parse("{sog}\n| B~Bb |\n{eog}"), ChordNotation.GERMAN).chordNames())
    }

    @Test
    fun `a German-notated song reads in the app's own notation`() {
        val song = ChordProParser.parse("{key: B}\n\n[B]a [H7]b [Bb]c")

        assertEquals("Bb", song.metadata.key)
        assertEquals(listOf("Bb", "B7", "Bb"), song.chordNames())
        assertEquals(listOf("B", "H7", "B"), ChordProNotation.toNotation(song, ChordNotation.GERMAN).chordNames())
    }

    @Test
    fun `B becomes H and Bb becomes B`() {
        assertEquals("H", ChordProNotation.toGerman("B"))
        assertEquals("B", ChordProNotation.toGerman("Bb"))
        assertEquals("B", ChordProNotation.toGerman("B♭"))
    }

    @Test
    fun `the quality and the extensions of the chord survive`() {
        assertEquals("Hm", ChordProNotation.toGerman("Bm"))
        assertEquals("Hmaj7", ChordProNotation.toGerman("Bmaj7"))
        assertEquals("Hm7b5", ChordProNotation.toGerman("Bm7b5"))
        assertEquals("Bsus4", ChordProNotation.toGerman("Bbsus4"))
        assertEquals("Bm7", ChordProNotation.toGerman("Bbm7"))
    }

    @Test
    fun `B sharp keeps its sign on the new letter`() {
        assertEquals("H#", ChordProNotation.toGerman("B#"))
    }

    @Test
    fun `the other six letters are left alone`() {
        listOf("C", "C#", "Db", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "A#", "Am7", "Gsus4").forEach { name ->
            assertEquals(name, ChordProNotation.toGerman(name))
        }
    }

    @Test
    fun `the bass note is rewritten by the same rule as the root`() {
        assertEquals("C/H", ChordProNotation.toGerman("C/B"))
        assertEquals("F/B", ChordProNotation.toGerman("F/Bb"))
        assertEquals("B/H", ChordProNotation.toGerman("Bb/B"))
        assertEquals("Hm7/A", ChordProNotation.toGerman("Bm7/A"))
    }

    @Test
    fun `a song already written in German notation stays as it is`() {
        assertEquals("H", ChordProNotation.toGerman("H"))
        assertEquals("Hm7", ChordProNotation.toGerman("Hm7"))
        assertEquals("B", ChordProNotation.toGerman("Hb")) // An unusual, but unambiguous, way of writing Bb.
    }

    @Test
    fun `words that are not chords are returned unchanged`() {
        assertEquals("N.C.", ChordProNotation.toGerman("N.C."))
        assertEquals("Bridge", ChordProNotation.toGerman("Bridge"))
        assertEquals("Break", ChordProNotation.toGerman("Break"))
        assertEquals("", ChordProNotation.toGerman(""))
    }

    @Test
    fun `the key the chords and the grid are rewritten but the annotations are not`() {
        val song = ChordProNotation.toNotation(ChordProParser.parse("{key: Bb}\n\n[B]a [Bb]b [*hold B]c\n\n{sog}\n| B . | Bb . |\n{eog}"), ChordNotation.GERMAN)

        assertEquals("B", song.metadata.key)
        assertEquals(listOf("H", "B", "H", "B"), song.chordNames())
        assertEquals(listOf("hold B"), song.annotationNames())
    }

    @Test
    fun `the chords above a tab are rewritten and the tablature is not`() {
        val song = ChordProNotation.toNotation(ChordProParser.parse("{sot}\n  B     Bm\ne|--0--11-|\nB|--1--2--|\n{eot}"), ChordNotation.GERMAN)

        assertEquals(listOf("  H     Hm", "e|--0--11-|", "B|--1--2--|"), song.tabLines())
    }

    @Test
    fun `a comment inside a grid does not hide the chords after it from the notation`() {
        val song = ChordProNotation.toNotation(ChordProParser.parse("{sog}\n| Am . |\n{c: x}\n| B . |\n{eog}"), ChordNotation.GERMAN)

        assertEquals(listOf("Am", "H"), song.chordNames())
    }

    @Test
    fun `a shorter chord name keeps the columns of a tab`() {
        // Bb is a character wider than the B it becomes, so the space it leaves behind is put back after it.
        val song = ChordProNotation.toNotation(ChordProParser.parse("{sot}\n  Bb    Bb\ne|--0--3--|\n{eot}"), ChordNotation.GERMAN)

        assertEquals(listOf("  B     B ", "e|--0--3--|"), song.tabLines())
    }

    @Test
    fun `a text typed in German notation is written in the standard one even without an H`() {
        // An F major chart: nothing in it says German, but the B its writer typed is a B flat all the same.
        assertEquals("{key: F}\n[F]a [Bb]b [C7]c", ChordProNotation.convertText("{key: F}\n[F]a [B]b [C7]c", ChordNotation.GERMAN, ChordNotation.STANDARD))
    }

    @Test
    fun `a standard text is shown in German notation everywhere it names a chord`() {
        val text = "{key: Bb}\n[B]a [Bb7/F]b [*hold B]c\n{sog}\n| B . | Bb . |\n{eog}"

        assertEquals(
            "{key: B}\n[H]a [B7/F]b [*hold B]c\n{sog}\n| H . | B . |\n{eog}",
            ChordProNotation.convertText(text, ChordNotation.STANDARD, ChordNotation.GERMAN),
        )
    }

    @Test
    fun `a standard text comes back from German notation as it was`() {
        val text = "{title: T}\n{key: Bm}\n\n[Bm]a [b]b [Bb]c [D/b]d [A#]e\n{sot}\n  Bb    B\ne|--0--3--|\n{eot}\n"
        val german = ChordProNotation.convertText(text, ChordNotation.STANDARD, ChordNotation.GERMAN)

        assertEquals("[Hm]a [h]b [B]c [D/h]d [A#]e", german.lines()[3])
        assertEquals(text, ChordProNotation.convertText(german, ChordNotation.GERMAN, ChordNotation.STANDARD))
    }

    @Test
    fun `a standard text is returned as it is`() {
        val text = "{title: Hello}\n[Am]Hello [B]there [*Hm?]\n"

        assertEquals(text, ChordProNotation.convertText(text, ChordNotation.STANDARD, ChordNotation.STANDARD))
    }

    @Test
    fun `a file with an H is brought into the standard notation`() {
        assertEquals(
            "[Bb]a [B7]b [b]c",
            ChordProNotation.convertText("[B]a [H7]b [h]c", ChordNotation.STANDARD, ChordNotation.STANDARD),
        )
        assertEquals("[Bb]a [C#]b", ChordProNotation.convertText("[B♭]a [C♯]b", ChordNotation.STANDARD, ChordNotation.STANDARD))
    }

    @Test
    fun `the chords a song defines are renamed with its chords`() {
        assertEquals(
            "{define: H frets 1 1 3 3 3 1}\n{key: H}\n[H]x [B]y",
            ChordProNotation.convertText("{define: B frets 1 1 3 3 3 1}\n{key: B}\n[B]x [Bb]y", ChordNotation.STANDARD, ChordNotation.GERMAN),
        )
        assertEquals(
            "  {chord:  Bb7 base-fret 1}\n{define: B frets x 2 4 4 4 2}\n[B]x [Bb7]y",
            ChordProNotation.convertText("  {chord:  B7 base-fret 1}\n{define: H frets x 2 4 4 4 2}\n[H]x [B7]y", ChordNotation.GERMAN, ChordNotation.STANDARD),
        )
    }

    @Test
    fun `a definition that names no chord or is delegated is left alone`() {
        val text = "{define: Bridge frets 1 1 3 3 3 1}\n{start_of_textblock}\n{define: B frets 1 1 3 3 3 1}\n{end_of_textblock}\n[B]x"

        assertEquals(
            "{define: Bridge frets 1 1 3 3 3 1}\n{start_of_textblock}\n{define: B frets 1 1 3 3 3 1}\n{end_of_textblock}\n[H]x",
            ChordProNotation.convertText(text, ChordNotation.STANDARD, ChordNotation.GERMAN),
        )
    }

    @Test
    fun `a text with definitions comes back from German notation as it was`() {
        val text = "{define: B frets x 2 4 4 4 2}\n{chord: Bb}\n[B]x [Bb]y"
        val german = ChordProNotation.convertText(text, ChordNotation.STANDARD, ChordNotation.GERMAN)

        assertEquals(text, ChordProNotation.convertText(german, ChordNotation.GERMAN, ChordNotation.STANDARD))
    }

    @Test
    fun `a text parsed as German is German without an H`() {
        assertEquals(listOf("F", "Bb"), ChordProParser.parse("[F]a [B]b", ChordNotation.GERMAN).chordNames())
        assertEquals(listOf("F", "B"), ChordProParser.parse("[F]a [B]b").chordNames())
        assertEquals("Bb", ChordProParser.summarize("{key: B}\n[F]a", ChordNotation.GERMAN).metadata.key)
        assertEquals("Bb", ChordProSummaryCache(ChordNotation.GERMAN).summaryOf("{key: B}\n[F]a").metadata.key)
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
