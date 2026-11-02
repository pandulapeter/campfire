/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.convert

import com.pandulapeter.campfire.chordpro.ChordProParser
import com.pandulapeter.campfire.chordpro.model.ChordProBlock
import com.pandulapeter.campfire.chordpro.model.ChordProLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChordSheetRomanChordsTest {
    private fun convert(text: String) = ChordSheetConverter.convert(ChordSheet.ofPlainText(text)).single()

    private fun chords(text: String) = ChordProParser.parse(text).blocks.filterIsInstance<ChordProBlock.Section>()
        .flatMap { it.lines }.filterIsInstance<ChordProLine.Lyrics>().flatMap { line -> line.chords.map { it.name } }

    @Test
    fun `rows of numerals are read as the chords they name in the song's key`() {
        val result = convert("Title\n\nKey: G\n\nI          IV\nI walk the line\nV              I\nBecause you're mine")

        assertTrue("[G]I walk the [C]line\n[D]Because you're [G]mine" in result, result)
    }

    @Test
    fun `a minor key's numerals count on its own scale unless the sheet marks a step with a flat`() {
        assertEquals(
            listOf("Dm", "F", "Gm", "A7"),
            chords(convert("Title\n\nKey: Dm\n\ni          III\nHalf past twelve\niv         V7\nNo one to hear my prayer")),
        )
        assertEquals(
            listOf("Am", "F", "C", "G"),
            chords(convert("Title\n\nKey: Am\n\ni          bVI\nHalf past twelve\nbIII       bVII\nNo one to hear my prayer")),
        )
    }

    @Test
    fun `a key change mid-song counts the numerals after it from the new key`() {
        val result = convert("Title\n\nKey: C\n\nI     IV    V\nSing it now\n\nKey: D\n\nI     IV    V\nSing it higher")

        assertEquals(listOf("C", "F", "G", "D", "G", "A"), chords(result))
        assertTrue("{key: D}" in result, result)
    }

    @Test
    fun `numerals are left as they are where the song names no key`() {
        val result = convert("Title\n\nI          IV\nI walk the line\nV          I\nBecause you're mine")

        assertEquals(emptyList(), chords(result).filter { it in listOf("C", "F", "G") })
        assertFalse("[C]" in result, result)
    }

    @Test
    fun `a lyric of the word I, of numeral-like words, or of words like vi and mix stays a lyric among numeral rows`() {
        val result = convert("Title\n\nKey: G\n\nI          IV\nI walk the line\nI\nI I\nI I I\ni\nvi and mix\nV          I\nBecause you're mine")

        assertTrue("\nI\nI I\nI I I\ni\nvi and mix\n" in result, result)
        assertEquals(listOf("G", "C", "D", "G"), chords(result))
    }

    @Test
    fun `a lone i is a chord in a song whose rows play it`() {
        assertEquals(
            listOf("Dm", "F", "Dm", "C"),
            chords(convert("Title\n\nKey: Dm\n\ni          III\nHalf past twelve\ni\nWatching the late show\nVII        i\nAll alone")).take(4),
        )
    }

    @Test
    fun `numerals all in capitals or all in small letters take their qualities from words, and are left alone where no word says one`() {
        assertEquals(
            listOf("C", "Am", "Dm7", "G7"),
            chords(convert("Title\n\nKey: C\n\nI          VIm\nHello there world\nII-7       V7\nGoodbye my friend")),
        )
        assertEquals(
            listOf("C", "Am", "F", "G"),
            chords(convert("Title\n\nKey: C\n\ni          vim\nHello there world\niv         v\nGoodbye my friend")),
        )
        assertEquals(emptyList(), chords(convert("Title\n\nKey: Am\n\ni          iv\nHello there world\nv          i\nGoodbye my friend")))
    }

    @Test
    fun `a quality word one space after its numeral is part of it`() {
        assertEquals(listOf("C", "Eaug", "F"), chords(convert("Title\n\nKey: C\n\nI          III aug   IV\nHello there world\nI          IV\nGoodbye my friend")).take(3))
    }

    @Test
    fun `a margin label beside a row of numerals stays the section's label`() {
        val sheet = listOf(
            "        Title",
            "        Key: G",
            "",
            "        I            IV",
            "V       Amazing grace how sweet",
            "        V              I",
            "        the sound that saved",
            "        IV           I",
            "C       Oh happy day when Jesus",
            "        V              I",
            "        washed my sins away",
        ).joinToString("\n")
        val result = convert(sheet)

        assertTrue("{start_of_verse: V}\n[G]Amazing grace [C]how sweet" in result, result)
        assertTrue("{start_of_chorus: C}\n[C]Oh happy day [G]when Jesus" in result, result)
    }

    @Test
    fun `verses numbered in numerals over rows of chords keep their numbers`() {
        val result = convert("Title\n\nKey: G\n\nI\nG       C\nAmazing grace\n\nII\nG       D\nThrough many dangers\n\nIII\nG       C\nWhen we've been there")

        assertEquals(listOf("G", "C", "G", "D", "G", "C"), chords(result))
        assertTrue("II" in result && "III" in result, result)
    }
}
