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

/** Where a caret stands in a document after a transposition rewrote it, see [ChordProTransposer.transposedOffset]. */
internal object ChordProOffsetMapping {

    /** [ChordProTransposer.transposedOffset], which is documented there. */
    fun transposedOffset(before: String, after: String, offset: Int): Int {
        val clamped = offset.coerceIn(0, before.length)
        if (before == after) return clamped
        val beforeStarts = ChordProLines.lineStartOffsets(before)
        val afterStarts = ChordProLines.lineStartOffsets(after)
        if (beforeStarts.size != afterStarts.size) return clamped.coerceAtMost(after.length)
        val line = lastLineStartingAtOrBefore(beforeStarts, clamped)
        val column = clamped - beforeStarts[line]
        val beforeLine = ChordProLines.splitLines(before)[line]
        val afterLine = ChordProLines.splitLines(after)[line]
        val afterLineStart = afterStarts[line]
        if (column > beforeLine.length) {
            // On the line break, or past the final one. The break is measured on the new text, since joinLines may
            // have given a file that mixed its endings a different separator.
            val limit = if (line + 1 < afterStarts.size) afterStarts[line + 1] - 1 else after.length
            return (afterLineStart + afterLine.length + (column - beforeLine.length)).coerceAtMost(limit)
        }
        return afterLineStart + transposedColumn(beforeLine, afterLine, column).coerceIn(0, afterLine.length)
    }

    private fun lastLineStartingAtOrBefore(starts: IntArray, offset: Int): Int {
        var low = 0
        var high = starts.lastIndex
        while (low < high) {
            val middle = (low + high + 1) / 2
            if (starts[middle] <= offset) low = middle else high = middle - 1
        }
        return low
    }

    private fun transposedColumn(before: String, after: String, column: Int): Int {
        if (before == after) return column
        val beforeBrackets = ChordProDirectives.brackets(before)
        val afterBrackets = ChordProDirectives.brackets(after)
        if (beforeBrackets.size == afterBrackets.size && beforeBrackets.isNotEmpty()) {
            var shift = 0
            beforeBrackets.forEachIndexed { index, bracket ->
                val oldRange = bracket.range
                val newRange = afterBrackets[index].range
                when {
                    column <= oldRange.first -> return column + shift
                    // Right before the "]" is after the whole chord name, which is where it stays.
                    column == oldRange.last -> return newRange.last
                    column < oldRange.last -> return (newRange.first + (column - oldRange.first)).coerceAtMost(newRange.last)
                    else -> shift = newRange.last - oldRange.last
                }
            }
            return column + shift
        }
        val prefix = before.commonPrefixWith(after).length
        val suffix = minOf(before.commonSuffixWith(after).length, minOf(before.length, after.length) - prefix)
        // The two ends meet only where the line merely grew at the caret (a key of C becoming C#), and a caret there
        // stays after what grew, the way it stays after the whole of a chord name that did.
        return when {
            column >= before.length - suffix -> after.length - (before.length - column)
            column <= prefix -> column
            else -> (after.length - suffix).coerceAtLeast(prefix)
        }
    }
}
