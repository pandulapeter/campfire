/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.chords

import com.pandulapeter.campfire.chordpro.ChordNotation
import com.pandulapeter.campfire.chordpro.edit.ChordProHighlighter
import com.pandulapeter.campfire.chordpro.edit.ChordProHighlighter.TokenType
import com.pandulapeter.campfire.chordpro.ChordProParser
import com.pandulapeter.campfire.chordpro.ChordProSplitter
import com.pandulapeter.campfire.chordpro.edit.ChordProTabWrapper
import com.pandulapeter.campfire.chordpro.convert.ChordSheet
import com.pandulapeter.campfire.chordpro.convert.ChordSheetConverter
import com.pandulapeter.campfire.chordpro.model.ChordProBlock
import com.pandulapeter.campfire.chordpro.model.ChordProLine
import com.pandulapeter.campfire.chordpro.model.GridToken
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChordProLatinNotationTest {

    private val roots = listOf("C", "D", "E", "F", "G", "A", "B")
    private val accidentals = listOf("", "#", "b")
    private val suffixes = listOf(
        "", "m", "mi", "min", "maj", "Maj", "M", "dim", "aug", "sus", "add", "+", "-", "°", "ø", "Δ",
        "7", "m7", "maj7", "M7", "dim7", "m7b5", "sus2", "sus4", "add9", "6", "69", "9", "11", "13", "5", "7alt",
        "7#9", "7b9", "mmaj7", "mMaj7", "7(9)", "6/9", "m(add9)", "7sus4", "9no3", "aug7", "+7",
    )

    private fun generatedNames() = roots.flatMap { root ->
        accidentals.flatMap { accidental ->
            suffixes.flatMap { suffix -> listOf("", "/E", "/Bb").map { bass -> "$root$accidental$suffix$bass" } }
        }
    }.filter(ChordProChordNames::isChordName)

    @Test
    fun `a Latin name is read as the chord it names`() {
        assertEquals("Am", ChordProChordNames.latinExpanded("Lam"))
        assertEquals("Bb7", ChordProChordNames.latinExpanded("Sib7"))
        assertEquals("Bm", ChordProChordNames.latinExpanded("Sim"))
        assertEquals("Cm7", ChordProChordNames.latinExpanded("Dom7"))
        assertEquals("Gsus4", ChordProChordNames.latinExpanded("Solsus4"))
        assertEquals("Eb5", ChordProChordNames.latinExpanded("Mib5"))
        assertEquals("Fadd9", ChordProChordNames.latinExpanded("Faadd9"))
        assertEquals("C/E", ChordProChordNames.latinExpanded("DO/MI"))
        assertEquals("A/C#", ChordProChordNames.latinExpanded("La/Do#"))
        assertEquals("D/f#", ChordProChordNames.latinExpanded("Re/fa#"))
        assertEquals("(G)", ChordProChordNames.latinExpanded("(Sol)"))
        assertEquals("D", ChordProChordNames.latinExpanded("Ré"))
        assertEquals("C", ChordProChordNames.latinExpanded("Dó"))
        assertEquals("F", ChordProChordNames.latinExpanded("Fá"))
        assertEquals("Am", ChordProChordNames.latinExpanded("Lám"))
        assertEquals("G", ChordProChordNames.latinExpanded("SOL"))
    }

    @Test
    fun `words are not Latin chords`() {
        listOf("do", "la", "sol", "Solo", "Refrain", "Fade", "La la", "Sole", "Mix", "Do-re", "Lab7x").forEach {
            assertNull(ChordProChordNames.latinExpanded(it), it)
        }
    }

    @Test
    fun `no name is a chord in both the standard and the Latin reading`() {
        val latin = generatedNames().map { ChordProNotation.toLatin(it) }
        generatedNames().forEach { name -> assertNull(ChordProChordNames.latinExpanded(name), name) }
        latin.forEach { name -> assertTrue(!ChordProChordNames.isChordName(name), name) }
    }

    @Test
    fun `standard to Latin and back is the identity`() {
        generatedNames().forEach { name -> assertEquals(name, ChordProChordNames.latinExpanded(ChordProNotation.toLatin(name)), name) }
    }

    @Test
    fun `a name is written in Latin notation`() {
        assertEquals("Lam", ChordProNotation.toLatin("Am"))
        assertEquals("Sib", ChordProNotation.toLatin("Bb"))
        assertEquals("Sim", ChordProNotation.toLatin("Bm"))
        assertEquals("Dom7", ChordProNotation.toLatin("Cm7"))
        assertEquals("Solsus4", ChordProNotation.toLatin("Gsus4"))
        assertEquals("La/Do#", ChordProNotation.toLatin("A/C#"))
        assertEquals("Re/fa#", ChordProNotation.toLatin("D/f#"))
        assertEquals("N.C.", ChordProNotation.toLatin("N.C."))
        assertEquals("Bridge", ChordProNotation.toLatin("Bridge"))
    }

    @Test
    fun `a Latin file is parsed into the standard notation`() {
        val song = ChordProParser.parse("{key: Lam}\n[Lam]Hello [Fa]world [Sol7]and [Mi/Sol#]more")

        assertEquals("Am", song.metadata.key)
        assertEquals(listOf("Am", "F", "G7", "E/G#"), song.blocks.filterIsInstance<ChordProBlock.Section>().flatMap { it.lines }
            .filterIsInstance<ChordProLine.Lyrics>().flatMap { line -> line.chords.map { it.name } })
        assertEquals("Am", ChordProParser.summarize("{key: Lam}\n[Lam]Hello").metadata.key)
        assertTrue(ChordProParser.summarize("{key: Lam}\n[Lam]Hello").hasChords)
    }

    @Test
    fun `a key spelled out in a Romance language is read`() {
        assertEquals("G mayor", ChordProParser.parse("{key: Sol mayor}\n[Sol]la").metadata.key)
        assertEquals("A minore", ChordProParser.summarize("{key: La minore}\n[Lam]la").metadata.key)
        assertEquals("La mineur", ChordProNotation.toNotation(ChordProParser.parse("{key: A mineur}\n[Am]la"), ChordNotation.LATIN).metadata.key)
        // A major key spelled out in Spanish or Italian prefers what its major key prefers, not what a minor one would.
        assertEquals("[Ab]la", ChordProTransposer.transposeText("{key: G mayor}\n[G]la", 1).substringAfter("\n"))
        assertEquals("[Ab]la", ChordProTransposer.transposeText("{key: G maggiore}\n[G]la", 1).substringAfter("\n"))
        assertEquals("[G#m]la", ChordProTransposer.transposeText("{key: G menor}\n[Gm]la", 1).substringAfter("\n"))
    }

    @Test
    fun `a song is shown in Latin notation`() {
        val song = ChordProParser.parse("{key: Bb}\n{start_of_grid}\n| Am . G . |\n{end_of_grid}\n[Bb]la [D/f#]la\n{c: Intro: [C] [G]}")
        val latin = ChordProNotation.toNotation(song, ChordNotation.LATIN)

        assertEquals("Sib", latin.metadata.key)
        val lines = latin.blocks.filterIsInstance<ChordProBlock.Section>().flatMap { it.lines }
        assertEquals(listOf("Sib", "Re/fa#"), lines.filterIsInstance<ChordProLine.Lyrics>().flatMap { line -> line.chords.map { it.name } })
        assertEquals(listOf("Lam", "Sol"), lines.filterIsInstance<ChordProLine.Grid>().single().tokens.filterIsInstance<GridToken.Chord>().map { it.name })
        assertEquals("Intro: [Do] [Sol]", latin.blocks.filterIsInstance<ChordProBlock.Comment>().single().text)
    }

    @Test
    fun `a text is converted into Latin and back`() {
        val text = "{key: Am}\n{define: Am base-fret 1 frets x 0 2 2 1 0}\n[Am]Hello [Bb]world [C/E]and [N.C.] [Bridge]"
        val latin = ChordProNotation.convertText(text, ChordNotation.STANDARD, ChordNotation.LATIN)

        assertEquals("{key: Lam}\n{define: Lam base-fret 1 frets x 0 2 2 1 0}\n[Lam]Hello [Sib]world [Do/Mi]and [N.C.] [Bridge]", latin)
        assertEquals(text, ChordProNotation.convertText(latin, ChordNotation.LATIN, ChordNotation.STANDARD))
    }

    @Test
    fun `a Latin text's comments and definitions come back in the standard notation`() {
        assertEquals("{c: Intro: [G] [D]}\nla la", ChordProNotation.convertText("{c: Intro: [Sol] [Re]}\nla la", ChordNotation.LATIN, ChordNotation.STANDARD))
        assertEquals(
            "{define: Am base-fret 1 frets x 0 2 2 1 0}\nla",
            ChordProNotation.convertText("{define: Lam base-fret 1 frets x 0 2 2 1 0}\nla", ChordNotation.LATIN, ChordNotation.STANDARD),
        )
        val text = "{c: Intro: [G] [D]}\n{define: Am base-fret 1 frets x 0 2 2 1 0}\nla la"
        assertEquals(text, ChordProNotation.convertText(ChordProNotation.convertText(text, ChordNotation.STANDARD, ChordNotation.LATIN), ChordNotation.LATIN, ChordNotation.STANDARD))
        assertEquals("la la", ChordProNotation.convertText("la la", ChordNotation.LATIN, ChordNotation.STANDARD))
        val written = "{define: Lam base-fret 1 frets x 0 2 2 1 0}\n[Am]la"
        val song = ChordProParser.parse(written)
        assertEquals("Am", song.metadata.definitions.single().name)
        assertEquals("Bm", ChordProTransposer.transpose(song, 2).metadata.definitions.single().name)
        assertEquals("{define: Am base-fret 1 frets x 0 2 2 1 0}\n[Am]la", ChordProNotation.convertText(written, ChordNotation.STANDARD, ChordNotation.STANDARD))
        assertEquals("{c: [La] la la}\n[A]x", ChordProNotation.convertText("{c: [La] la la}\n[A]x", ChordNotation.STANDARD, ChordNotation.STANDARD))
    }

    @Test
    fun `a standard chord pasted into a Latin text is read as the chord it is`() {
        assertEquals("[Am]a [Am]b", ChordProNotation.convertText("[Lam]a [Am]b", ChordNotation.LATIN, ChordNotation.STANDARD))
    }

    @Test
    fun `a German file is shown in Latin`() {
        assertEquals("[Si]a [Sib]b", ChordProNotation.convertText("[H]a [B]b", ChordNotation.STANDARD, ChordNotation.LATIN))
    }

    @Test
    fun `a lowercase minor through a Latin editor comes back spelled out`() {
        val latin = ChordProNotation.convertText("[a]la [f#7]la", ChordNotation.STANDARD, ChordNotation.LATIN)

        assertEquals("[Lam]la [Fa#m7]la", latin)
        assertEquals("[Am]la [F#m7]la", ChordProNotation.convertText(latin, ChordNotation.LATIN, ChordNotation.STANDARD))
    }

    @Test
    fun `a Latin file is brought into the standard notation`() {
        assertEquals("[Am]la [Bb]la", ChordProNotation.convertText("[Lam]la [Sib]la", ChordNotation.STANDARD, ChordNotation.STANDARD))
        assertEquals(ChordProSplitter.comparable("[Am]la"), ChordProSplitter.comparable("[Lam]la"))
    }

    @Test
    fun `a text is never converted into a numbering`() {
        val text = "{key: C}\n[C]la [Lam]la"

        assertEquals("{key: C}\n[C]la [Am]la", ChordProNotation.convertText(text, ChordNotation.STANDARD, ChordNotation.NASHVILLE))
        assertEquals("{key: C}\n[C]la [Am]la", ChordProNotation.convertText(text, ChordNotation.ROMAN, ChordNotation.STANDARD))
    }

    @Test
    fun `a row of Latin names over a staff is renamed in its columns`() {
        val lines = listOf("Do   Sol  Lam  Fa", "e|---0----0----0----1-|")

        assertEquals(listOf("C    G    Am   F ", "e|---0----0----0----1-|"), ChordProTabTransposer.rewriteChordNames(lines) { ChordProNotation.read(it, isGerman = false) })
        assertEquals(listOf("Do Sol Lam Fa", "e|-0--0--0--1-|"), ChordProTabTransposer.rewriteChordNames(listOf("C G Am F", "e|-0--0--0--1-|"), ChordProNotation::toLatin))
    }

    @Test
    fun `a Latin row of chords travels with the lyrics under it`() {
        assertEquals(
            listOf(listOf("    Lam", "Hello darkness"), listOf("Do", "my old friend")),
            ChordProTabWrapper.wrapPreformatted(listOf("    Lam        Do", "Hello darkness my old friend"), maxColumns = 14),
        )
    }

    @Test
    fun `a Latin chord in a comment is coloured as a chord`() {
        val text = "{c: Intro: [Lam] [Solo]}"
        val chords = ChordProHighlighter.tokenize(text).filter { it.type == TokenType.CHORD }.map { text.substring(it.start, it.end) }

        assertEquals(listOf("[Lam]"), chords)
    }

    @Test
    fun `a Latin chord sheet is converted`() {
        val sheet = ChordSheet.ofPlainText("Key: Lam\n\nLam         Fa\nLa noche es larga\nDo             Sol\nY el camino es mio")
        val converted = ChordSheetConverter.convert(sheet).single()

        assertTrue("[Lam]La noche es [Fa]larga" in converted, converted)
        assertTrue("[Do]Y el camino es [Sol]mio" in converted, converted)
        assertEquals("La la la la\n", ChordSheetConverter.convert(ChordSheet.ofPlainText("La la la la")).single())
    }

    @Test
    fun `a Latin name is read and written by the chord readers`() {
        assertEquals(ChordProChords.parse("Am"), ChordProChords.parse("Lam", ChordNotation.LATIN))
        assertEquals(ChordProChords.parse("Bb7"), ChordProChords.parse("Sib7"))
        assertEquals(listOf("Do", "Mi", "Sol"), ChordProChords.noteNames(ChordProChords.parse("C")!!, ChordNotation.LATIN))
        assertEquals("Re", ChordProChords.transposedName("Do", 2, ChordNotation.LATIN))
        assertEquals(listOf("C", "E", "G"), ChordProChords.noteNames(ChordProChords.parse("C")!!, ChordNotation.NASHVILLE))
    }
}
