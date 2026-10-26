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

/** What a line of ABC is: an information field, a comment, or (anything else that isn't blank) a line of music. */
internal object AbcLines {

    const val CONTINUATION = '+'
    private const val COMMENT = '%'
    private const val ESCAPE = '\\'

    /** An information field (`T:Title`), its value without its trailing comment. */
    data class Field(val letter: Char, val value: String)

    /**
     * The field on [line], or null for a comment or a line of music. A field is a letter (or the `+` that continues
     * the field before it) and a colon at the start of the line. A line opening with a note and a repeat (`A:|`) is
     * music: no field's value starts with a bar line.
     */
    fun field(line: String): Field? {
        val trimmed = line.trimStart()
        val letter = trimmed.getOrNull(0)?.takeIf { it in 'A'..'Z' || it in 'a'..'z' || it == CONTINUATION } ?: return null
        if (trimmed.getOrNull(1) != ':' || trimmed.getOrNull(2).let { it == '|' || it == ':' }) return null
        return Field(letter, withoutComment(trimmed.substring(2)).trim())
    }

    fun isComment(line: String) = line.trimStart().startsWith(COMMENT)

    /** [text] up to its first `%` that no backslash escapes. */
    fun withoutComment(text: String): String {
        var index = 0
        while (index < text.length) {
            when (text[index]) {
                ESCAPE -> index++
                COMMENT -> return text.substring(0, index)
            }
            index++
        }
        return text
    }

    /**
     * The name of the block a `%%begintext`-style stylesheet line opens (`text`), whose lines up to `%%endtext` are
     * free text or PostScript rather than music, or null for any other line.
     */
    fun blockOpenedBy(line: String) = line.trim().takeIf { it.startsWith("%%begin") }?.removePrefix("%%begin")?.takeWhile { it.isLetter() }

    fun closesBlock(line: String, name: String) = line.trim().startsWith("%%end$name")
}
