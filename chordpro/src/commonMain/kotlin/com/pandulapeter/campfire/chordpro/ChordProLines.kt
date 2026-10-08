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

/** Splits a file into its lines and joins them back the way the file wrote them, see [splitLines]. */
internal object ChordProLines {

    /** Splits into lines accepting both `\r\n` and `\n`; a trailing newline does not create an extra line. */
    fun splitLines(text: String): List<String> {
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
        val lines = normalized.split('\n')
        return if (lines.size > 1 && lines.last().isEmpty()) lines.subList(0, lines.size - 1) else lines
    }

    /**
     * The line separator [text] is written with: CRLF where any line of it ends that way, a bare CR where the file
     * uses those and no CRLF, LF otherwise. It is one answer for the whole file, so a file mixing its endings comes
     * out of an edit written with the one that wins, which is the separator such a file was most likely meant to
     * have. The CR-only case is an old Mac export, and it is answered so that an edit of one line leaves every
     * other byte of such a file alone, the way it does for the other two.
     */
    fun lineSeparatorOf(text: String) = when {
        text.contains("\r\n") -> "\r\n"
        text.contains('\r') -> "\r"
        else -> "\n"
    }

    /**
     * The character offset every line of [splitLines] starts at in [text]. It is counted on the text itself rather
     * than from the lengths of the lines, because a file may mix its line endings, and a break is one or two
     * characters depending on which of them ended the line above.
     */
    internal fun lineStartOffsets(text: String): IntArray {
        val starts = mutableListOf(0)
        var offset = 0
        while (offset < text.length) {
            when (text[offset]) {
                '\r' -> {
                    offset += if (text.getOrNull(offset + 1) == '\n') 2 else 1
                    starts += offset
                }

                '\n' -> {
                    offset++
                    starts += offset
                }

                else -> offset++
            }
        }
        // The start past a final line break is not a line, see splitLines.
        if (starts.size > 1 && endsWithLineBreak(text)) starts.removeAt(starts.lastIndex)
        return starts.toIntArray()
    }

    /** Whether [text] ends with a line break, which [splitLines] does not report as a line of its own. */
    fun endsWithLineBreak(text: String) = text.endsWith("\n") || text.endsWith("\r")

    /**
     * The inverse of [splitLines] for one particular [original]: the separator it is written with, see
     * [lineSeparatorOf], and the trailing line break put back where the original had one, so that an edit of one line
     * leaves every other byte of the file as it was.
     */
    fun joinLines(lines: List<String>, original: String): String {
        val separator = lineSeparatorOf(original)
        val joined = lines.joinToString(separator)
        return if (endsWithLineBreak(original) && lines.isNotEmpty()) joined + separator else joined
    }
}
