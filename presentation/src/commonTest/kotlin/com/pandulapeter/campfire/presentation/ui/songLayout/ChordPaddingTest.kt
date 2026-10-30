/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songLayout

import com.pandulapeter.campfire.chordpro.ChordProParser
import com.pandulapeter.campfire.chordpro.language.WritingDirection
import com.pandulapeter.campfire.chordpro.model.ChordProBlock
import com.pandulapeter.campfire.chordpro.model.ChordProLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChordPaddingTest {

    /** The line the way ChordPro writes it, each chord in brackets where it sits. */
    private fun lyrics(line: String): ChordProLine.Lyrics {
        val text = StringBuilder()
        val chords = mutableListOf<ChordProLine.Lyrics.Chord>()
        var index = 0
        while (index < line.length) {
            if (line[index] == '[') {
                val end = line.indexOf(']', index)
                chords += ChordProLine.Lyrics.Chord(position = text.length, name = line.substring(index + 1, end), isAnnotation = false)
                index = end + 1
            } else {
                text.append(line[index++])
            }
        }
        return ChordProLine.Lyrics(text = text.toString(), chords = chords)
    }

    /** Every character is 10 wide, and every chord [chordWidth], the gap included. */
    private fun ChordProLine.Lyrics.padded(chordWidth: Float = 45f, paragraphDirection: WritingDirection? = WritingDirection.LEFT_TO_RIGHT) =
        padLyricsToFitChords(
            chordWidths = chords.map { chordWidth },
            gap = 0f,
            paddingWidth = 10f,
            measureWidth = { it.length * 10f },
            paragraphDirection = paragraphDirection,
        )

    @Test
    fun `marked padding opens each run of padding with a mark and leaves the text's own spaces unmarked`() {
        fun ChordProLine.Lyrics.padded(marksPadding: Boolean) =
            padLyricsToFitChords(chordWidths = listOf(70f, 10f), gap = 0f, paddingWidth = 10f, measureWidth = { it.length * 10f }, paragraphDirection = null, marksPadding = marksPadding)
        val line = lyrics("[Am]is a [C]house")
        assertEquals("is a\u00A0\u2060\u00A0\u00A0\u200Bhouse", line.padded(marksPadding = true).text)
        assertEquals(line.padded(marksPadding = false).text, line.padded(marksPadding = true).text.replace("\u2060", ""))
    }

    @Test
    fun `extra padding goes before the offsets asked for, the chords and formatting moving with the text`() {
        val line = lyrics("lo[Em]ve and [C]more").copy(
            formatting = listOf(
                ChordProLine.Lyrics.Formatting(0, 4, ChordProLine.Lyrics.Formatting.Style.BOLD),
                ChordProLine.Lyrics.Formatting(9, 13, ChordProLine.Lyrics.Formatting.Style.ITALIC),
            ),
        )
        val padded = line.padLyricsToFitChords(
            chordWidths = listOf(10f, 10f),
            gap = 0f,
            paddingWidth = 10f,
            measureWidth = { it.length * 10f },
            paragraphDirection = null,
            marksPadding = true,
            extraPadding = mapOf(0 to 2, 5 to 1, 9 to 1),
        )

        assertEquals("\u2060\u00A0\u00A0love \u2060\u00A0and \u200B\u2060\u00A0more", padded.text)
        assertEquals(listOf('v', 'm'), padded.chords.map { padded.text[it.position] })
        assertEquals(listOf("love", "more"), padded.formatting.map { padded.text.substring(it.start, it.end) })
    }

    @Test
    fun `a chord only line breaks between its chords and nowhere else`() {
        val padded = lyrics("[C] [G] [Am] [F]").padded()
        assertFalse(' ' in padded.text)
        assertEquals(3, padded.text.count { it == '​' })
        assertFalse(padded.text.endsWith('​'))
        padded.chords.drop(1).forEach { chord -> assertEquals('​', padded.text[chord.position - 1]) }
    }

    @Test
    fun `formatting covers the same words after padding, never the padding`() {
        val line = lyrics("[C]a b [G]cd").copy(
            formatting = listOf(
                ChordProLine.Lyrics.Formatting(0, 1, ChordProLine.Lyrics.Formatting.Style.BOLD),
                ChordProLine.Lyrics.Formatting(2, 6, ChordProLine.Lyrics.Formatting.Style.ITALIC),
            ),
        )
        val padded = line.padded(chordWidth = 80f)

        assertEquals("a", padded.text.substring(padded.formatting[0].start, padded.formatting[0].end))
        assertTrue(padded.text.substring(padded.formatting[1].start, padded.formatting[1].end).endsWith("cd"))
        assertEquals('b', padded.text[padded.formatting[1].start])
    }

    @Test
    fun `a chord row breaks before its chords and never before a mark`() {
        val line = ChordProParser.parse("[C]   | [G] x4").blocks.filterIsInstance<ChordProBlock.Section>().single().lines.single() as ChordProLine.Lyrics
        listOf(5f, 45f).forEach { chordWidth ->
            val padded = line.padded(chordWidth = chordWidth)

            padded.chords.zipWithNext().filter { (_, next) -> next.isMark }.forEach { (chord, mark) ->
                val before = padded.text.substring(chord.position, mark.position)
                assertFalse(before.any { it == ' ' || it == '\u200B' }, "$chordWidth: ${chord.name} ${mark.name}")
            }
            assertEquals('\u200B', padded.text[padded.chords.single { it.name == "G" }.position - 1])
        }
    }

    @Test
    fun `a word is never broken between its chords`() {
        assertFalse('​' in lyrics("wo[C]n[G]der").padded().text)
    }

    @Test
    fun `a padded fragment keeps its trailing space with its chord`() {
        val padded = lyrics("[C]Hello [G]world").padded(chordWidth = 100f)
        assertEquals(1, padded.text.count { it == '​' })
        assertFalse(padded.text.endsWith('​'))
        assertTrue(padded.text.startsWith("Hello "))
        assertEquals('​', padded.text[padded.chords[1].position - 1])
    }

    @Test
    fun `the inner spaces of a fragment stay ordinary`() {
        val padded = lyrics("[C]Hello world [G]x").padded(chordWidth = 30f)
        assertTrue(padded.text.startsWith("Hello world ​"))
    }

    @Test
    fun `chord positions point at their fragments`() {
        val padded = lyrics("[C]Hello [G]world [Am]again").padded(chordWidth = 100f)
        assertEquals('H', padded.text[padded.chords[0].position])
        assertEquals('w', padded.text[padded.chords[1].position])
        assertEquals('a', padded.text[padded.chords[2].position])
    }

    @Test
    fun `a line read the paragraph's way is padded the same in either direction or none`() {
        val latin = lyrics("[C]Hello [G]world [Am]again")
        assertEquals(latin.padded(paragraphDirection = null), latin.padded(paragraphDirection = WritingDirection.LEFT_TO_RIGHT))
        val hebrew = lyrics("[Am]שלום [C]עולם [G]שוב")
        assertEquals(hebrew.padded(paragraphDirection = null), hebrew.padded(paragraphDirection = WritingDirection.RIGHT_TO_LEFT))
    }

    @Test
    fun `the padding of a latin word or a number in a hebrew line keeps to its own run`() {
        mapOf("[Am]שלום [C]Hello [G]עולם" to "Hello", "[Am]שיר [C]1 [D]2 [G]עולם" to "1").forEach { (line, word) ->
            val padded = lyrics(line).padded(chordWidth = 100f, paragraphDirection = WritingDirection.RIGHT_TO_LEFT)
            val piece = padded.text.substring(padded.chords[1].position, padded.chords[2].position)
            assertTrue(piece.startsWith("$word\u200E\u00A0"), line)
            assertTrue(piece.endsWith("\u00A0\u200E\u200B"), line)
        }
    }

    @Test
    fun `the padding of a hebrew word in a latin line keeps to its own run`() {
        val padded = lyrics("[C]Hello [Am]שלום [G]world").padded(chordWidth = 100f)
        val piece = padded.text.substring(padded.chords[1].position, padded.chords[2].position)
        assertEquals(2, piece.count { it == '\u200F' })
        assertTrue(piece.startsWith("שלום\u200F"))
    }

    @Test
    fun `formatting keeps its words around a direction mark`() {
        val line = lyrics("[Am]שלום [C]Hello [G]עולם").copy(
            formatting = listOf(ChordProLine.Lyrics.Formatting(5, 11, ChordProLine.Lyrics.Formatting.Style.BOLD)),
        )
        val padded = line.padded(chordWidth = 100f, paragraphDirection = WritingDirection.RIGHT_TO_LEFT)
        assertEquals("Hello\u200E\u00A0", padded.text.substring(padded.formatting.single().start, padded.formatting.single().end))
    }

    @Test
    fun `every script is padded with no-break spaces`() {
        listOf('a'.code, 'ש'.code, 'ب'.code, '1'.code, ' '.code).forEach { assertEquals('\u00A0', chordPaddingAfter(it)) }
    }
}
