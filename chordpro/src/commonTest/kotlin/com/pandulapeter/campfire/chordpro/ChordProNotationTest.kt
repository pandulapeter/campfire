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

class ChordProNotationTest {

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
        val song = ChordProNotation.toGerman(ChordProParser.parse("{key: Bb}\n\n[B]a [Bb]b [*hold B]c\n\n{sog}\n| B . | Bb . |\n{eog}"))

        assertEquals("B", song.metadata.key)
        assertEquals(listOf("H", "B", "H", "B"), song.chordNames())
        assertEquals(listOf("hold B"), song.annotationNames())
    }

    @Test
    fun `the chords above a tab are rewritten and the tablature is not`() {
        val song = ChordProNotation.toGerman(ChordProParser.parse("{sot}\n  B     Bm\ne|--0--11-|\nB|--1--2--|\n{eot}"))

        assertEquals(listOf("  H     Hm", "e|--0--11-|", "B|--1--2--|"), song.tabLines())
    }

    @Test
    fun `a shorter chord name keeps the columns of a tab`() {
        // Bb is a character wider than the B it becomes, so the space it leaves behind is put back after it.
        val song = ChordProNotation.toGerman(ChordProParser.parse("{sot}\n  Bb    Bb\ne|--0--3--|\n{eot}"))

        assertEquals(listOf("  B     B ", "e|--0--3--|"), song.tabLines())
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
