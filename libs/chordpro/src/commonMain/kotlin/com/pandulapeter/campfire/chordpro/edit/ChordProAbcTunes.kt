/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.edit

import com.pandulapeter.campfire.chordpro.abc.AbcTokens
import com.pandulapeter.campfire.chordpro.syntax.ChordProLineScanner
import com.pandulapeter.campfire.chordpro.syntax.ChordProLines
import com.pandulapeter.campfire.chordpro.syntax.ChordProVocabulary.ABC

/**
 * The ABC tunes of a ChordPro text as the song is drawn from it, each with where its lines stand in the text, so that
 * what a renderer reports about a tune can be marked where it was typed.
 */
public object ChordProAbcTunes {

    /**
     * One tune: the lines of an `abc` block up to a blank line or a directive, which the parser makes a run of its own,
     * joined by `\n` as the notation renders them ([text]).
     */
    public class Tune internal constructor(
        public val text: String,
        private val lines: List<String>,
        private val lineStarts: IntArray,
    ) {

        /**
         * The range of the song's text of the token a renderer reported at [line] and [column] of [text]
         * ([AbcTokens.around]): the whole line where [column] is null, and a column on the line's end, or a line or column
         * before or past the tune, moved back to the last character there is. Null where the tune has nothing to mark.
         */
        public fun rangeAt(line: Int, column: Int?): IntRange? {
            if (lines.isEmpty()) return null
            if (line < 0) return rangeAt(0, 0)
            if (line > lines.lastIndex) return rangeAt(lines.lastIndex, lines.last().length)
            if (column == null) {
                val content = lines[line].indexOfFirst { !it.isWhitespace() }.takeIf { it >= 0 } ?: return null
                return lineStarts[line] + content..lineStarts[line] + lines[line].indexOfLast { !it.isWhitespace() }
            }
            if (column < 0) return if (line == 0) rangeAt(0, 0) else rangeAt(line - 1, lines[line - 1].length)
            val token = AbcTokens.around(lines[line], column) ?: return if (line == 0) null else rangeAt(line - 1, lines[line - 1].length)
            return lineStarts[line] + token.first..lineStarts[line] + token.last
        }
    }

    /** Every tune of [text], in order. */
    public fun of(text: String): List<Tune> {
        val lines = ChordProLines.splitLines(text)
        val lineStarts = ChordProLines.lineStartOffsets(text)
        val tunes = mutableListOf<Tune>()
        val tuneLines = mutableListOf<Int>()
        fun endTune() {
            if (tuneLines.isEmpty()) return
            val ownLines = tuneLines.map { lines[it] }
            tunes += Tune(text = ownLines.joinToString("\n"), lines = ownLines, lineStarts = IntArray(tuneLines.size) { lineStarts[tuneLines[it]] })
            tuneLines.clear()
        }
        ChordProLineScanner.scan(lines).forEach { scanned ->
            if (scanned.environment == ABC && scanned.directive == null && scanned.trimmed.isNotEmpty()) tuneLines += scanned.index else endTune()
        }
        endTune()
        return tunes
    }
}
