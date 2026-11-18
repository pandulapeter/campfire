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

import com.pandulapeter.campfire.chordpro.language.WritingDirection
import com.pandulapeter.campfire.chordpro.language.writingDirectionOf

/**
 * The inline boxes of the line being read, held until the line goes on, since a flex container lays its items out by
 * their `order` before their order in the document, and their margins tell words from syllables only once both sides
 * of a gap are known.
 *
 * A page that sets each chord over its syllable in a box of its own (a chord row above a lyric fragment, each a block
 * inside an `inline-block`) loses the chords' places in its plain text, which has every row of every box on a line of
 * its own. Such a box is a cell: a first row that is nothing but chord names, ended by a block or a break, then a second
 * row under it, which may be empty (a chord sung between words) and must be there, so that a box holding one lyric word
 * that happens to spell a chord (`A`) stays a word. Cells flow on one line, as any inline box does. Where whitespace or a
 * margin stands between two of them they are two words; where neither does, they are one word whose syllables carry
 * chords (`מלא` and `כים`, `מלאכים`). Which margins meet depends on the direction the line runs in, decided by its own
 * letters: in a line read from the right, the box after another stands to its left.
 */
internal class ChordSheetHtmlCells {

    private class Cell(
        val chords: List<String>,
        val text: String,
        val order: Int,
        val marginLeft: Double,
        val marginRight: Double,
        val isSpaced: Boolean,
    )

    private val pending = mutableListOf<Cell>()
    private var isSpaced = false

    val isPending get() = pending.isNotEmpty()

    /** Whitespace in the source between two boxes. */
    fun space() {
        isSpaced = true
    }

    /**
     * Holds [box], laid out by [style], for the line: a cell, or a box of one row as a word of that row; false for a box
     * of any other shape, which the reader then reads as it reads any element.
     */
    fun add(box: ChordSheetHtmlReader.Box, style: ChordSheetHtmlStyle): Boolean {
        val rows = box.lines.withIndex().filterNot { it.value.isEmpty }
        if (rows.size > MAX_ROWS) return false
        val first = rows.firstOrNull() ?: return true
        val chords = first.value.text.split(' ', '\u00a0').filter { it.isNotEmpty() }.map { ChordSheetHtml.chordIn(it) ?: "" }
        val isCell = first.index + 1 < box.brokenLines && chords.isNotEmpty() && "" !in chords
        if (!isCell && rows.size > 1) return false
        pending += Cell(
            chords = if (isCell) chords else emptyList(),
            text = (if (isCell) rows.getOrNull(1) else first)?.value?.text?.toString()?.trim().orEmpty(),
            order = style.order,
            marginLeft = style.marginLeft ?: 0.0,
            marginRight = style.marginRight ?: 0.0,
            isSpaced = isSpaced,
        )
        isSpaced = false
        return true
    }

    /** Writes the held boxes onto [line] in the order they are laid out in, a space between two words. */
    fun flushInto(line: ChordSheetHtmlReader.Line) {
        if (pending.isEmpty()) return
        val cells = pending.sortedBy { it.order }
        pending.clear()
        isSpaced = false
        val isRightToLeft = writingDirectionOf(cells.joinToString(" ") { it.text }) == WritingDirection.RIGHT_TO_LEFT
        val hasChords = cells.any { it.chords.isNotEmpty() }
        var previous: Cell? = null
        for (cell in cells) {
            // An arrow among chord cells shows which way the chord-only line is read, not something played or sung.
            if (hasChords && cell.chords.isEmpty() && cell.text.isNotEmpty() && cell.text.all { it.isWhitespace() || it.isArrow() }) continue
            val isSeparated = previous != null && (cell.isSpaced || gapBetween(previous, cell, isRightToLeft) > GAP)
            if (isSeparated && line.text.isNotEmpty() && line.text.last() != ' ') line.text.append(' ')
            cell.chords.forEach { line.chords += line.text.length to it }
            // A chord between two words keeps its room on the line, so that the next word's chord stays over that word.
            line.text.append(cell.text.ifEmpty { " ".repeat(cell.chords.sumOf { it.length + 1 }) })
            previous = cell
        }
    }

    private fun gapBetween(before: Cell, after: Cell, isRightToLeft: Boolean) =
        if (isRightToLeft) before.marginLeft + after.marginRight else before.marginRight + after.marginLeft

    private fun Char.isArrow() = code in 0x2190..0x21FF || code in 0x27F0..0x27FF || code in 0x2900..0x297F || code in 0x2B00..0x2B11

    companion object {
        /** The rows a cell has: its chords and its lyric. */
        const val MAX_ROWS = 2

        /** The widest gap, in pixels, two boxes of one word may leave between them: rounding, not a space. */
        private const val GAP = 0.5
    }
}
