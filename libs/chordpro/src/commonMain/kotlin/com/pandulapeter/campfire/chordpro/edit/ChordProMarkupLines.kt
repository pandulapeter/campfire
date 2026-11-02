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

import com.pandulapeter.campfire.chordpro.ChordProParser
import com.pandulapeter.campfire.chordpro.syntax.ChordProDirectives
import com.pandulapeter.campfire.chordpro.syntax.ChordProLineScanner
import com.pandulapeter.campfire.chordpro.syntax.ChordProLines

/** Where in a ChordPro text the parser reads `<b>`, `<i>`, and `<u>` markup, see [markupRanges]. */
public object ChordProMarkupLines {

    /** The comments whose text the parser keeps its markup in, as `ChordProBlock.Comment`. */
    private val commentNames = setOf("comment", "c", "comment_italic", "ci", "comment_box", "cb", "highlight")

    /** The plain comments a Campfire 3 file wrote its section headings as. */
    private val plainCommentNames = setOf("comment", "c")

    /**
     * For every line of [text], split as the parser splits it (one entry per line, a line break at the very end making
     * none), the offsets into [text] that markup written on that line is read in, or null where the parser reads none.
     *
     * - A lyric line, a blank one included, is the whole line without its break, leading and trailing whitespace
     *   included: a caller wrapping it trims what it wraps itself.
     * - A comment the parser keeps markup in (`{c}`, `{ci}`, `{cb}`, and `{highlight}`, by any of their spellings, but
     *   no selector name such as `c-guitar`, which the parser drops) is the offsets of its value, trimmed. An empty
     *   value (`{c: }`) is an empty range at the closing brace, so that a caret there still finds the line; a comment
     *   with no value at all is null. A comment inside a tab or a grid is still a comment; inside an environment handed
     *   to another program only one written with a colon is.
     * - A plain comment with no environment open whose value is a Campfire 3 heading (`{c: Verse 1}`) is null: the
     *   parser opens a section from the value's first letters, so tags there would turn the heading into a comment.
     * - Any other directive, a `#` line, and a line of a tab, a grid, or delegated notation are null.
     *
     * The text is read in one walk, so asking for a whole song costs one parse.
     */
    public fun markupRanges(text: String): List<IntRange?> {
        val lineStarts = ChordProLines.lineStartOffsets(text)
        return ChordProLineScanner.scan(text).map { line -> line.markupRange(lineStarts[line.index]) }.toList()
    }

    private fun ChordProLineScanner.ScannedLine.markupRange(lineStart: Int): IntRange? {
        val directive = directive
        if (directive != null) {
            if (directive.name !in commentNames) return null
            if (directive.name in plainCommentNames && environment == null && ChordProParser.isLegacyHeading(directive.value.orEmpty())) return null
            val valueIndex = ChordProDirectives.directiveValueStart(trimmed) ?: return null
            // The indices count into the trimmed line; trim and trimStart both stop at Char.isWhitespace.
            val trimmedStart = lineStart + raw.length - raw.trimStart().length
            var valueStart = valueIndex
            var valueEnd = trimmed.length - 1
            while (valueStart < valueEnd && trimmed[valueStart].isWhitespace()) valueStart++
            while (valueEnd > valueStart && trimmed[valueEnd - 1].isWhitespace()) valueEnd--
            return (trimmedStart + valueStart) until (trimmedStart + valueEnd)
        }
        if (isSourceComment || isLiteral) return null
        return lineStart until lineStart + raw.length
    }
}
