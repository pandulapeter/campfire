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

/**
 * Splits a file that contains several songs separated by `{new_song}` / `{ns}` directives.
 */
object ChordProSplitter {

    fun split(text: String): List<String> {
        val parts = mutableListOf<MutableList<String>>(mutableListOf())
        ChordProSyntax.splitLines(text).forEach { rawLine ->
            val directive = ChordProSyntax.matchDirective(rawLine.trim())
            if (directive != null && (directive.name == "new_song" || directive.name == "ns")) {
                parts += mutableListOf<String>()
            } else {
                parts.last() += rawLine
            }
        }
        return parts.map { part -> part.dropWhile { it.isBlank() }.dropLastWhile { it.isBlank() }.joinToString("\n") }
            .filter { it.isNotBlank() }
    }

    /**
     * [text] folded the way [split] folds a song, and the way the text editors of this module keep a file's own line
     * endings: LF throughout, and no blank lines at either end. Two texts with the same comparable form are the same
     * song as a file, which is the question an import asks of a part [split] handed it and the file already on disk.
     */
    fun comparable(text: String) = ChordProSyntax.splitLines(text)
        .dropWhile { it.isBlank() }
        .dropLastWhile { it.isBlank() }
        .joinToString("\n")
}
