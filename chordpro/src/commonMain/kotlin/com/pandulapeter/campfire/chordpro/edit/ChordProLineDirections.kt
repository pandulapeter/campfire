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

import com.pandulapeter.campfire.chordpro.language.WritingDirection
import com.pandulapeter.campfire.chordpro.language.writingDirectionOf
import com.pandulapeter.campfire.chordpro.syntax.ChordProLineScanner
import com.pandulapeter.campfire.chordpro.syntax.ChordProLines

/** The direction each line of a ChordPro text is written in on its own, whatever the song's, see [directionOfLineAt]. */
public object ChordProLineDirections {

    /** The comments whose value the reader shows as text, by any of their spellings. */
    private val commentNames = setOf("comment", "c", "comment_italic", "ci", "comment_box", "cb", "highlight")

    /**
     * The direction the line of [text] holding [offset] is written in by itself, read by the parser's line rules: a
     * directive, a `#` line, and a line of a tab, a grid, or an environment handed to another program
     * ([ChordProLiteralLines]) are left to right, being source or notation; a comment's value and a lyric line, its
     * chords in brackets left out, go the way most of their strong letters do ([writingDirectionOf]), a line of no letters
     * left to right. An [offset] past the end is on the last line.
     *
     * One walk over the lines up to [offset], so it is asked once per key press, never per frame.
     */
    public fun directionOfLineAt(text: String, offset: Int): WritingDirection {
        val prefix = text.substring(0, offset.coerceIn(0, text.length))
        val lineIndex = ChordProLines.splitLines("$prefix.").lastIndex
        val line = ChordProLineScanner.scan(text).elementAtOrNull(lineIndex) ?: return WritingDirection.LEFT_TO_RIGHT
        val directive = line.directive
        return when {
            directive != null -> if (directive.name.lowercase() in commentNames) {
                writingDirectionOf(directive.value.orEmpty())
            } else {
                WritingDirection.LEFT_TO_RIGHT
            }
            line.isSourceComment || line.isLiteral -> WritingDirection.LEFT_TO_RIGHT
            else -> writingDirectionOf(withoutChords(line.raw))
        }
    }

    /** [line] without its `[…]` chords, an unclosed bracket kept as text, as the parser keeps it. */
    private fun withoutChords(line: String) = buildString {
        var index = 0
        while (index < line.length) {
            val close = if (line[index] == '[') line.indexOf(']', startIndex = index + 1) else -1
            if (close >= 0) {
                index = close + 1
            } else {
                append(line[index])
                index++
            }
        }
    }
}
