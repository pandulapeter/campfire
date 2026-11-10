/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.abc

/**
 * The tokens of a line of ABC, as far as a message about one has to find it: a renderer names the character it stopped
 * at, and the editor marks the whole thing that has to change around it.
 */
public object AbcTokens {

    /**
     * The token of [line] at [column] (clamped into the line), as a range of the line: a chord symbol or annotation in
     * quotes, a decoration between `!`, a group of grace notes in braces, or an inline field in brackets, each whole and
     * to the end of the line where it is never closed; otherwise the characters around [column] up to a space or a bar
     * line. A column on a space or past the end is moved back to the token before it, null on a line of spaces only.
     */
    public fun around(line: String, column: Int): IntRange? {
        var at = column.coerceIn(0, line.length)
        while (at > 0 && (at >= line.length || line[at].isWhitespace())) at--
        if (at >= line.length || line[at].isWhitespace()) return null
        enclosing(line, at)?.let { return it }
        if (line[at] in BAR_LINES) return at..at
        var start = at
        while (start > 0 && !line[start - 1].isWhitespace() && line[start - 1] !in BAR_LINES) start--
        var end = at
        while (end + 1 < line.length && !line[end + 1].isWhitespace() && line[end + 1] !in BAR_LINES) end++
        return start..end
    }

    /** The quoted text, decoration, grace notes, or inline field [at] is in, read from the start of [line], or null. */
    private fun enclosing(line: String, at: Int): IntRange? {
        var index = 0
        while (index <= at) {
            val closing = when (line[index]) {
                '"' -> '"'
                '!' -> '!'
                '{' -> '}'
                '[' -> ']'.takeIf { index + 2 < line.length && line[index + 1].isLetter() && line[index + 2] == ':' }
                '%' -> return null
                else -> null
            }
            if (closing == null) {
                index++
                continue
            }
            val end = line.indexOf(closing, index + 1).let { if (it < 0) line.lastIndex else it }
            if (at <= end) return index..end
            index = end + 1
        }
        return null
    }

    private const val BAR_LINES = "|"
}
