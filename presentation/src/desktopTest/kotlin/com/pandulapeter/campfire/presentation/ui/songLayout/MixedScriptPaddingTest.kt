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

import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.pandulapeter.campfire.chordpro.language.WritingDirection
import com.pandulapeter.campfire.chordpro.model.ChordProLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Lays padded mixed-script lines out with the real bidirectional layout, to see which side each gap opens on. */
internal class MixedScriptPaddingTest {
    private val measurer = TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr)

    private fun layout(text: String, direction: TextDirection) =
        measurer.measure(text, TextStyle(fontSize = 16.sp, textDirection = direction), softWrap = false)

    /** The line padded so that its second chord's piece is 200 wide, laid out in [direction]. */
    private fun padded(text: String, chordPositions: List<Int>, direction: WritingDirection): Pair<ChordProLine.Lyrics, TextLayoutResult> {
        val textDirection = if (direction == WritingDirection.RIGHT_TO_LEFT) TextDirection.Rtl else TextDirection.Ltr
        val line = ChordProLine.Lyrics(
            text = text,
            chords = chordPositions.map { ChordProLine.Lyrics.Chord(position = it, name = "C", isAnnotation = false) },
        ).padLyricsToFitChords(
            chordWidths = chordPositions.indices.map { if (it == 1) 200f else 0f },
            gap = 0f,
            paddingWidth = layout(PADDING.toString(), textDirection).size.width.toFloat(),
            measureWidth = { layout(it, textDirection).size.width.toFloat() },
            paragraphDirection = direction,
        )
        return line to layout(line.text, textDirection)
    }

    /** Whether every padding character of the second piece lies on [isRight] of the piece's [word]. */
    private fun assertPaddingBeside(line: ChordProLine.Lyrics, layout: TextLayoutResult, word: String, isRight: Boolean) {
        val start = line.chords[1].position
        val end = line.chords[2].position
        assertEquals(word, line.text.substring(start, start + word.length))
        val wordBoxes = (start until start + word.length).map(layout::getBoundingBox)
        val wordLeft = wordBoxes.minOf { it.left }
        val wordRight = wordBoxes.maxOf { it.right }
        val padding = (start until end).filter { line.text[it] == PADDING }
        assertTrue(padding.size > 1)
        padding.forEach { offset ->
            val box = layout.getBoundingBox(offset)
            if (isRight) assertTrue(box.left >= wordRight - 0.5f, "$word: padding at ${box.left}, word ends at $wordRight")
            else assertTrue(box.right <= wordLeft + 0.5f, "$word: padding at ${box.right}, word starts at $wordLeft")
        }
    }

    @Test
    fun `a latin word's gap in a hebrew line opens to its right, where its chord hangs`() {
        val (line, layout) = padded("שלום Hello עולם", listOf(0, 5, 11), WritingDirection.RIGHT_TO_LEFT)
        assertEquals(ResolvedTextDirection.Ltr, layout.getBidiRunDirection(line.chords[1].position))
        assertPaddingBeside(line, layout, "Hello", isRight = true)
    }

    @Test
    fun `a number's gap in a hebrew line opens to its right, where its chord hangs`() {
        val (line, layout) = padded("שיר 12 עולם", listOf(0, 4, 7), WritingDirection.RIGHT_TO_LEFT)
        assertEquals(ResolvedTextDirection.Ltr, layout.getBidiRunDirection(line.chords[1].position))
        assertPaddingBeside(line, layout, "12", isRight = true)
    }

    @Test
    fun `a hebrew word's gap in a hebrew line opens to its left`() {
        val (line, layout) = padded("Hello שלום עולם", listOf(0, 6, 11), WritingDirection.RIGHT_TO_LEFT)
        assertPaddingBeside(line, layout, "שלום", isRight = false)
    }

    @Test
    fun `a hebrew word's gap in a latin line opens to its left, where its chord hangs`() {
        val (line, layout) = padded("Hello שלום world", listOf(0, 6, 11), WritingDirection.LEFT_TO_RIGHT)
        assertEquals(ResolvedTextDirection.Rtl, layout.getBidiRunDirection(line.chords[1].position))
        assertPaddingBeside(line, layout, "שלום", isRight = false)
    }

    @Test
    fun `latin words in a hebrew line keep their order around a gap`() {
        val (line, layout) = padded("שלום Hello world", listOf(0, 5, 11), WritingDirection.RIGHT_TO_LEFT)
        val hello = layout.getBoundingBox(line.chords[1].position)
        val world = layout.getBoundingBox(line.chords[2].position)
        assertTrue(hello.right < world.left)
        assertPaddingBeside(line, layout, "Hello", isRight = true)
    }
}
