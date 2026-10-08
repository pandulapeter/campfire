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
import com.pandulapeter.campfire.chordpro.ChordProParser
import com.pandulapeter.campfire.chordpro.edit.ChordProTabWrapper
import com.pandulapeter.campfire.chordpro.model.ChordProBlock
import com.pandulapeter.campfire.chordpro.model.ChordProLine
import com.pandulapeter.campfire.chordpro.model.ChordProSong
import com.pandulapeter.campfire.chordpro.model.GridToken
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ChordProNashvilleTest {

    private fun nashville(name: String, key: String = "C") = ChordProNashville.number(name, ChordProNashville.tonicOf(key)!!, isRoman = false)
    private fun roman(name: String, key: String = "C") = ChordProNashville.number(name, ChordProNashville.tonicOf(key)!!, isRoman = true)

    private fun lyricChords(song: ChordProSong) = song.blocks.flatMap { block ->
        when (block) {
            is ChordProBlock.Section -> listOf(block)
            is ChordProBlock.ChorusRecall -> block.blocks.filterIsInstance<ChordProBlock.Section>()
            else -> emptyList()
        }
    }.flatMap { it.lines }.filterIsInstance<ChordProLine.Lyrics>().flatMap { line -> line.chords.map { it.name } }

    private fun shown(text: String, notation: ChordNotation = ChordNotation.NASHVILLE, transposition: Int = 0) =
        ChordProNotation.toNotation(ChordProTransposer.transpose(ChordProParser.parse(text), transposition), notation)

    @Test
    fun `every step is numbered in a major key`() {
        assertEquals(
            listOf("1", "b2", "2", "b3", "3", "4", "#4", "5", "b6", "6", "b7", "7"),
            listOf("C", "Db", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B").map { nashville(it) },
        )
        assertEquals(listOf("1", "4", "5", "6-"), listOf("Bb", "Eb", "F", "Gm").map { nashville(it, key = "Bb") })
        assertEquals(listOf("1", "4", "5", "6-", "#4"), listOf("E", "A", "B", "C#m", "A#").map { nashville(it, key = "E") })
        // The steps between are written the same whatever the accidentals say.
        assertEquals("#4", nashville("Gb"))
        assertEquals("b7", nashville("A#"))
    }

    @Test
    fun `a minor chord is written with a dash and every other quality is kept`() {
        assertEquals("6-", nashville("Am"))
        assertEquals("6-7", nashville("Am7"))
        assertEquals("6-7", nashville("Ami7"))
        assertEquals("6-7b5", nashville("Amin7b5"))
        assertEquals("6-maj7", nashville("Ammaj7"))
        assertEquals("1maj7", nashville("Cmaj7"))
        assertEquals("1M7", nashville("CM7"))
        assertEquals("7dim", nashville("Bdim"))
        assertEquals("5sus4", nashville("Gsus4"))
        assertEquals("1(7)", nashville("C7"))
        assertEquals("1(6/9)", nashville("C6/9"))
    }

    @Test
    fun `a bass note is a step of its own`() {
        assertEquals("5/7", nashville("G/B"))
        assertEquals("2/#4", nashville("D/f#"))
        assertEquals("(4)", nashville("(F)"))
    }

    @Test
    fun `a word that is no chord is left as it is`() {
        assertEquals("N.C.", nashville("N.C."))
        assertEquals("Chorus 2x", nashville("Chorus 2x"))
    }

    @Test
    fun `a minor key is numbered from its own tonic`() {
        listOf("Am", "A minor", "a-moll", "Am (capo 2)").forEach { key ->
            assertEquals(listOf("1-", "b6", "b3", "b7", "5(7)"), listOf("Am", "F", "C", "G", "E7").map { nashville(it, key) }, key)
        }
        assertEquals("7°", nashville("G#°", "Am"))
        assertEquals(listOf("1-", "b6", "b3", "b7"), lyricChords(shown("{key: Em}\n[Em]a [C]b [G]c [D]d")))
    }

    @Test
    fun `a song with no key or an unreadable one stays in letters`() {
        val noKey = ChordProParser.parse("[C]a [G]b")
        assertSame(noKey, ChordProNotation.toNotation(noKey, ChordNotation.NASHVILLE))
        assertEquals(listOf("C", "G"), lyricChords(shown("{key: unknown}\n[C]a [G]b")))
        assertNull(ChordProNashville.tonicOf("Unknown"))
    }

    @Test
    fun `the key stays in letters, and so does the key a modulation names`() {
        val song = shown("{key: G}\n[G]a [C]b\n{transpose: 2}\n{key: A}\n[G]c [C]d")

        assertEquals("G", song.metadata.key)
        assertTrue(song.blocks.filterIsInstance<ChordProBlock.Transpose>().single().key.orEmpty().all { it.isLetter() })
        // The stretch after the modulation was moved up by two, and is counted from the key it moved to.
        assertEquals(listOf("1", "4", "1", "4"), lyricChords(song))
    }

    @Test
    fun `a chorus recalled after a modulation reads the same numbers`() {
        val song = shown("{key: C}\n{soc}\n[C]a [F]b [G]c\n{eoc}\n{transpose: 2}\n{chorus}")

        assertEquals(listOf("1", "4", "5", "1", "4", "5"), lyricChords(song))
    }

    @Test
    fun `a transposed song is numbered the same`() {
        assertEquals(lyricChords(shown("{key: C}\n[C]a [Am]b [F]c [G7]d")), lyricChords(shown("{key: C}\n[C]a [Am]b [F]c [G7]d", transposition = 5)))
        assertEquals("F", shown("{key: C}\n[C]a", transposition = 5).metadata.key)
    }

    @Test
    fun `grids, comments and the chord rows of tabs are numbered`() {
        val song = shown("{key: C}\n{start_of_grid}\n| C . F . |\n{end_of_grid}\n{start_of_tab}\nC    G\ne|--0----3--|\n{end_of_tab}\n{c: Intro: [G] [Am]}")
        val lines = song.blocks.filterIsInstance<ChordProBlock.Section>().flatMap { it.lines }

        assertEquals(listOf("1", "4"), lines.filterIsInstance<ChordProLine.Grid>().single().tokens.filterIsInstance<GridToken.Chord>().map { it.name })
        assertEquals("Intro: [5] [6-]", song.blocks.filterIsInstance<ChordProBlock.Comment>().single().text)
        assertEquals("1    5", lines.filterIsInstance<ChordProLine.Tab>().first().text)
    }

    @Test
    fun `the definitions are left in letters`() {
        val song = shown("{key: C}\n{define: G base-fret 1 frets 3 2 0 0 0 3}\n[G]a")

        assertEquals("G", song.metadata.definitions.single().name)
    }

    @Test
    fun `Roman numerals carry the quality in the numeral`() {
        assertEquals(listOf("I", "IV", "V", "vi"), listOf("C", "F", "G", "Am").map { roman(it) })
        assertEquals("V7", roman("G7"))
        assertEquals("ii7", roman("Dm7"))
        assertEquals("vii°", roman("Bdim"))
        assertEquals("viiø7", roman("Bm7b5"))
        assertEquals("viiø7", roman("Bø7"))
        assertEquals("I+", roman("Caug"))
        assertEquals("I+", roman("C+"))
        assertEquals("IVmaj7", roman("Fmaj7"))
        assertEquals("Vsus4", roman("Gsus4"))
        assertEquals("V/7", roman("G/B"))
        assertEquals("I/3", roman("C/E"))
        assertEquals("i°7", roman("Cdim7"))
        assertEquals("vi6", roman("Am6"))
        assertEquals("viMaj7", roman("AmMaj7"))
        assertEquals(
            listOf("I", "bII", "II", "bIII", "III", "IV", "#IV", "V", "bVI", "VI", "bVII", "VII"),
            listOf("C", "Db", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B").map { roman(it) },
        )
        assertEquals(listOf("i", "ii", "biii", "iv", "v", "vi", "bvii"), listOf("Cm", "Dm", "Ebm", "Fm", "Gm", "Am", "Bbm").map { roman(it) })
    }

    @Test
    fun `a minor key is numbered from its own tonic in numerals too`() {
        assertEquals(listOf("i", "bVI", "bIII", "bVII", "V7"), lyricChords(shown("{key: Am}\n[Am]a [F]b [C]c [G]d [E7]e", ChordNotation.ROMAN)))
    }

    @Test
    fun `a degree is recognized as a page shows it`() {
        listOf(
            "1", "6-", "b7", "5(7)", "5/7", "1(6/9)", "2-7", "#4", "bVII", "viiø7", "IV/3", "vii°", "I+", "V7", "ii7", "b7(7sus4)", "#4(6/9)",
            "5(7)/7", "1(7)(b9)", "(5(7))", "2(7alt)", "1(add9)",
        ).forEach {
            assertTrue(ChordProNashville.isDegree(it), it)
        }
        listOf("I", "Iv", "IIII", "8", "Verse", "Intro", "x2", "b", "VIIII").forEach { assertFalse(ChordProNashville.isDegree(it), it) }
    }

    @Test
    fun `a row of numbers over a staff travels with its columns, and a lyric line of I does not`() {
        assertTrue(ChordProChordNames.isDisplayedChordName("6-"))
        assertTrue(ChordProChordNames.isDisplayedChordName("Sol"))
        assertTrue(ChordProChordNames.isDisplayedChordName("IV"))
        assertEquals(
            listOf(listOf("    6-", "Hello darkness"), listOf("1", "my old friend")),
            ChordProTabWrapper.wrapPreformatted(listOf("    6-         1", "Hello darkness my old friend"), maxColumns = 14),
        )
        assertEquals(
            listOf(listOf("I I I I I I"), listOf("Hello darkness"), listOf("my old friend")),
            ChordProTabWrapper.wrapPreformatted(listOf("I I I I I I", "Hello darkness my old friend"), maxColumns = 14),
        )
        assertEquals(
            listOf(listOf("    5(7)", "Hello darkness"), listOf("#4(6/9)", "my old friend")),
            ChordProTabWrapper.wrapPreformatted(listOf("    5(7)       #4(6/9)", "Hello darkness my old friend"), maxColumns = 14),
        )
        assertEquals(
            listOf(listOf("(5(7)", "Hello darkness"), listOf("1(add9))", "my old friend")),
            ChordProTabWrapper.wrapPreformatted(listOf("(5(7)          1(add9))", "Hello darkness my old friend"), maxColumns = 14),
        )
        assertEquals(
            listOf(listOf("    C(add9)", "Hello darkness"), listOf("(G)  x2", "my old friend")),
            ChordProTabWrapper.wrapPreformatted(listOf("    C(add9)    (G)  x2", "Hello darkness my old friend"), maxColumns = 14),
        )
    }

    @Test
    fun `an extension that starts with a digit is set off in parentheses`() {
        assertEquals("b7(7sus4)", nashville("Bb7sus4"))
        assertEquals("b6(13)", nashville("Ab13"))
        assertEquals("#4(6/9)", nashville("F#6/9"))
        assertEquals("2(7#9)", nashville("D7#9"))
        assertEquals("1(5)", nashville("C5"))
        assertEquals("5(7)/7", nashville("G7/B"))
        assertEquals("1(6/9)/3", nashville("C6/9/E"))
        assertEquals("1(7)(b9)", nashville("C7(b9)"))
        assertEquals("1(add9)", nashville("C(add9)"))
        assertEquals("(5(7))", nashville("(G7)"))
        assertEquals("5sus", nashville("Gsus"))
        assertEquals("2-7", nashville("Dm7"))
        assertEquals("bVII7sus4", roman("Bb7sus4"))
        assertEquals(listOf("1", "5(7)"), lyricChords(shown("{key: C}\n[C]a [G7]b")))
        assertEquals(1, ChordProHighlighter.chordsOfShownText("Intro: [#4(6/9)]", ChordNotation.NASHVILLE).size)
    }

    @Test
    fun `the names a song shows are listed for its chords`() {
        val song = ChordProParser.parse("{key: C}\n[C]a [G/B]b\n{transpose: 2}\n[D]c")
        val moved = ChordProTransposer.transpose(song, 0)

        assertEquals(mapOf("C" to "1", "G/B" to "5/7", "E" to "2"), ChordProNotation.shownNames(moved, ChordNotation.NASHVILLE))
        assertEquals(mapOf("C" to "Do", "G/B" to "Sol/Si", "E" to "Mi"), ChordProNotation.shownNames(moved, ChordNotation.LATIN))
    }

    @Test
    fun `a text and a summary given a numbering are read as standard`() {
        assertEquals("Bb", ChordProParser.summarize("{key: Bb}\n[B]a", ChordNotation.NASHVILLE).metadata.key)
        assertEquals(listOf("B"), lyricChords(ChordProParser.parse("[B]a", ChordNotation.ROMAN)))
    }

    @Test
    fun `numbers in a comment are coloured as chords only in a numbering`() {
        assertEquals(1, ChordProHighlighter.chordsOfShownText("Intro: [5] [Verse]", ChordNotation.NASHVILLE).size)
        assertEquals(0, ChordProHighlighter.chordsOfShownText("Intro: [5] [Verse]").size)
    }
}
