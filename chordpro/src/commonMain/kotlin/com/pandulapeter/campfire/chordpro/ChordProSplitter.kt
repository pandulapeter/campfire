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
        var delegatedEnvironment: String? = null
        ChordProSyntax.splitLines(text.withoutByteOrderMarks()).forEach { rawLine ->
            val trimmedLine = rawLine.trim()
            // Inside an environment handed to another program a `{ns}` is that program's text, as the parser reads it. Any
            // `{end_of_…}` closes it and any `{start_of_…}` written with a value moves it on, whichever environment they
            // name, as they do for the parser; one the file never closes takes the rest of the file, which the parser does too.
            if (delegatedEnvironment != null) {
                ChordProSyntax.matchDelegatedDirective(trimmedLine)?.name?.let { name ->
                    ChordProSyntax.startOfEnvironment(name)?.let { environment ->
                        delegatedEnvironment = environment.takeIf { it in ChordProSyntax.delegateEnvironments }
                    }
                    ChordProSyntax.endOfEnvironment(name)?.let { delegatedEnvironment = null }
                }
                parts.last() += rawLine
                return@forEach
            }
            val directive = ChordProSyntax.matchDirective(trimmedLine)
            if (directive != null && (directive.name == "new_song" || directive.name == "ns")) {
                parts += mutableListOf<String>()
            } else {
                delegatedEnvironment = directive?.name?.let(ChordProSyntax::startOfEnvironment)?.takeIf { it in ChordProSyntax.delegateEnvironments }
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
     * The chords are folded into the standard notation as well, since an import writes them that way: a German chart
     * imported again is the file it became the first time, and the same as one written before that was so. So is every
     * directive outside an environment handed to another program, into one spelling
     * ([ChordProSyntax.canonicalDirective]): a short name, a missing space or a capital is how a file was typed, not
     * what it says.
     */
    fun comparable(text: String): String {
        var delegatedEnvironment: String? = null
        return ChordProSyntax.splitLines(ChordProNotation.convertText(text.withoutByteOrderMarks(), ChordNotation.STANDARD, ChordNotation.STANDARD))
            .map { rawLine ->
                val trimmedLine = rawLine.trim()
                // The same walk through the delegated environments as split's, so that LilyPond's `{ c d e }` or an
                // ABC line is never read as a directive and respelled.
                if (delegatedEnvironment != null) {
                    ChordProSyntax.matchDelegatedDirective(trimmedLine)?.name?.let { name ->
                        ChordProSyntax.startOfEnvironment(name)?.let { environment ->
                            delegatedEnvironment = environment.takeIf { it in ChordProSyntax.delegateEnvironments }
                        }
                        ChordProSyntax.endOfEnvironment(name)?.let { delegatedEnvironment = null }
                    }
                    return@map rawLine
                }
                val directive = ChordProSyntax.matchDirective(trimmedLine) ?: return@map rawLine
                delegatedEnvironment = ChordProSyntax.startOfEnvironment(directive.name)?.takeIf { it in ChordProSyntax.delegateEnvironments }
                ChordProSyntax.canonicalDirective(directive)
            }
            .dropWhile { it.isBlank() }
            .dropLastWhile { it.isBlank() }
            .joinToString("\n")
    }

    /**
     * A byte order mark is never part of a song. Editors on Windows prefix a UTF-8 file with one, and joining two
     * such files — which is exactly what a file holding several songs is — leaves one sitting in the middle, where
     * `decodeLibraryText` does not reach and where `trim` will not touch it either: U+FEFF is a format character
     * and not whitespace, so a `{title}` line behind one is read as lyrics and the song loses its name.
     */
    private fun String.withoutByteOrderMarks() =
        if (BYTE_ORDER_MARK in this) filterNot { it == BYTE_ORDER_MARK } else this

    private const val BYTE_ORDER_MARK = '\uFEFF'
}
