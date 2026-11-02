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
import com.pandulapeter.campfire.chordpro.model.SectionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AbcToChordProTest {

    private fun metadataOf(tune: String) = ChordProParser.parse(AbcToChordPro.convert(tune).text).metadata

    private fun keyOf(field: String) = metadataOf("X:1\nT:Tune\nK:$field\nGABc|").key

    @Test
    fun `the titles, the composer, and the rhythm become the title, subtitle, artist, and tags`() {
        val song = AbcToChordPro.convert("X:7\nT:The Lantern Reel\nT:Lantern\nT:Third Name\nC:Campfire Players\nC:arr. Somebody\nR:reel\nK:G\nGABc|")
        val metadata = ChordProParser.parse(song.text).metadata

        assertEquals("The Lantern Reel", song.title)
        assertEquals("The Lantern Reel", metadata.title)
        assertEquals("Lantern", metadata.subtitle)
        assertEquals("Campfire Players", metadata.artist)
        assertEquals(listOf("reel"), metadata.tags)
    }

    @Test
    fun `a tune with no title takes the fallback title`() {
        val song = AbcToChordPro.convert("X:1\nK:G\nGABc|", fallbackTitle = "lantern_reel")

        assertEquals("lantern_reel", song.title)
        assertEquals("lantern_reel", ChordProParser.parse(song.text).metadata.title)
        assertNull(AbcToChordPro.convert("X:1\nK:G\nGABc|").title)
    }

    @Test
    fun `accented letters written with backslashes are decoded`() {
        assertEquals("Caf\u00e9 Sz\u00f3l\u00f3 \u0150r", metadataOf("X:1\nT:Caf\\'e Sz\\'ol\\'o \\HOr\nK:G\nG|").title)
        assertEquals("\u00e9 \u00df", metadataOf("X:1\nT:\\u00e9 \\ss\nK:G\nG|").title)
    }

    @Test
    fun `a key becomes the key its chords are in, a minor-like mode the tonic's minor`() {
        assertEquals("G", keyOf("G"))
        assertEquals("Bb", keyOf("Bb"))
        assertEquals("F#m", keyOf("F#m"))
        assertEquals("Am", keyOf("A minor"))
        assertEquals("Em", keyOf("Edor"))
        assertEquals("Dm", keyOf("D Dorian"))
        assertEquals("Bm", keyOf("Bphr"))
        assertEquals("Am", keyOf("AAeolian"))
        assertEquals("Bm", keyOf("Bloc"))
        assertEquals("D", keyOf("Dmix"))
        assertEquals("G", keyOf("G Mixolydian"))
        assertEquals("F", keyOf("Flyd"))
        assertEquals("C", keyOf("Cion"))
        assertEquals("Eb", keyOf("Ebmaj"))
    }

    @Test
    fun `clefs and explicit accidentals are dropped from the key, and some keys give none`() {
        assertEquals("G", keyOf("G clef=bass"))
        assertEquals("D", keyOf("D exp ^f ^c"))
        assertNull(keyOf("none"))
        assertNull(keyOf("HP"))
        assertNull(keyOf("clef=treble"))
    }

    @Test
    fun `a key change in the body leaves the opening key`() {
        assertEquals("G", metadataOf("X:1\nT:Tune\nK:G\n\"G\"GABc|\nK:D\n\"D\"DEFA|[K:A]\"A\"ABcd|").key)
    }

    @Test
    fun `the meter becomes the time, a compound one as its total`() {
        assertEquals("6/8", metadataOf("X:1\nT:Tune\nM:6/8\nK:G\nG|").time)
        assertEquals("C", metadataOf("X:1\nT:Tune\nM:C\nK:G\nG|").time)
        assertEquals("C|", metadataOf("X:1\nT:Tune\nM:C|\nK:G\nG|").time)
        assertEquals("5/8", metadataOf("X:1\nT:Tune\nM:(3+2)/8\nK:G\nG|").time)
        assertEquals("7/8", metadataOf("X:1\nT:Tune\nM:2+2+3/8\nK:G\nG|").time)
        assertNull(metadataOf("X:1\nT:Tune\nM:none\nK:G\nG|").time)
    }

    @Test
    fun `the tempo is the number after the beat`() {
        assertEquals("120", metadataOf("X:1\nT:Tune\nQ:1/4=120\nK:G\nG|").tempo)
        assertEquals("60", metadataOf("X:1\nT:Tune\nQ:\"Steady\" 3/8=60\nK:G\nG|").tempo)
        assertEquals("96", metadataOf("X:1\nT:Tune\nQ:96\nK:G\nG|").tempo)
        assertNull(metadataOf("X:1\nT:Tune\nQ:\"Lively\"\nK:G\nG|").tempo)
    }

    @Test
    fun `words after the tune become verses, a blank W line starting the next`() {
        val tune = "X:1\nT:Tune\nK:G\n\"G\"GABc|\nw:la la la la\nW:First line of one\nW:Second line of one\nW:\nW:First line of [two]\n+: and more"
        val verses = ChordProParser.parse(AbcToChordPro.convert(tune).text).blocks
            .filterIsInstance<ChordProBlock.Section>()
            .filter { it.type == SectionType.Verse }
            .map { section -> section.lines.filterIsInstance<ChordProLine.Lyrics>().map { it.text } }

        assertEquals(listOf(listOf("First line of one", "Second line of one"), listOf("First line of (two) and more")), verses)
    }

    @Test
    fun `the tune is kept whole in an ABC block`() {
        val tune = "X:1\nT:Tune\nK:G\n\"G\"GABc dedB|\nw:la la la la"
        val text = AbcToChordPro.convert(tune).text

        assertTrue(text.endsWith("{start_of_abc}\n$tune\n{end_of_abc}"))
        val block = ChordProParser.parse(text).blocks.filterIsInstance<ChordProBlock.Section>().single { it.type == SectionType.Custom("abc") }
        assertEquals(tune.lines().size, block.lines.size)
    }

    @Test
    fun `a line that would close the ABC block early becomes an ABC comment`() {
        val text = AbcToChordPro.convert("X:1\nT:Tune\nK:G\nGABc|\n{end_of_abc}\n{title: Other}\ndedB|").text
        val song = ChordProParser.parse(text)

        assertEquals("Tune", song.metadata.title)
        assertTrue("%{end_of_abc}\n%{title: Other}\ndedB|\n{end_of_abc}" in text)
    }

    @Test
    fun `a tune with no chord symbols gets no grid and says so`() {
        val song = AbcToChordPro.convert("X:1\nT:Tune\nK:G\n\"^Slowly\"GABc|dedB|")

        assertFalse(song.hasChords)
        assertFalse("start_of_grid" in song.text)
        assertFalse(ChordProParser.parse(song.text).hasChords)
    }
}
