/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songEditor

import androidx.compose.ui.text.TextRange
import com.pandulapeter.campfire.chordpro.edit.ChordProLineDirections
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The editor's own arrow keys over every song of :chordpro's corpus, with Windows line breaks too. */
class EditorArrowKeysPropertiesTest {

    private val corpus = File(requireNotNull(javaClass.getResource("/corpus")).toURI()).listFiles().orEmpty()
        .filter { it.isFile }.sortedBy { it.name }
        .flatMap { file -> file.readText().let { listOf(file.name to it, "${file.name} (CRLF)" to it.replace("\r\n", "\n").replace("\n", "\r\n")) } }

    @Test
    fun `stepping forward through a song and back stops at the same places, never inside a letter, its marks, or a line break`() = corpus.forEach { (name, text) ->
        val forward = generateSequence(0) { offset -> nextGraphemeOffset(text, offset).takeIf { it != offset } }.toList()
        val backward = generateSequence(text.length) { offset -> previousGraphemeOffset(text, offset).takeIf { it != offset } }.toList().reversed()
        assertEquals(forward, backward, name)
        assertEquals(text.length, forward.last(), name)
        forward.drop(1).dropLast(1).forEach { offset ->
            assertTrue(!text[offset].isCombining() && !text[offset].isLowSurrogate() && !(text[offset] == '\n' && text[offset - 1] == '\r'), "$name at $offset")
        }
    }

    @Test
    fun `a collapsed caret moved forward and back returns where it was, and a selection extended and shrunk keeps its anchor`() = corpus.forEach { (name, text) ->
        val stops = generateSequence(0) { offset -> nextGraphemeOffset(text, offset).takeIf { it != offset } }.toList()
        stops.dropLast(1).forEach { offset ->
            val forward = arrowKeySelection(text, TextRange(offset), isForward = true, isExtending = false)
            assertEquals(TextRange(offset), arrowKeySelection(text, forward, isForward = false, isExtending = false), "$name at $offset")
            val extended = arrowKeySelection(text, TextRange(offset), isForward = true, isExtending = true)
            assertEquals(TextRange(offset), arrowKeySelection(text, extended, isForward = false, isExtending = true), "$name at $offset")
        }
    }

    @Test
    fun `every caret position of a line is read in that line's one direction`() = corpus.forEach { (name, text) ->
        var lineStart = 0
        text.split('\n').forEach { line ->
            // The offset between a line's \r and \n is no place a caret stops.
            val directions = (lineStart..lineStart + line.removeSuffix("\r").length).map { ChordProLineDirections.directionOfLineAt(text, it) }.distinct()
            assertEquals(1, directions.size, "$name, line \"$line\": $directions")
            lineStart += line.length + 1
        }
    }

    private fun Char.isCombining() = category == CharCategory.NON_SPACING_MARK || category == CharCategory.ENCLOSING_MARK || category == CharCategory.COMBINING_SPACING_MARK
}
