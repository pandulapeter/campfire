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
 * Sets the languages a song is sung in directly in its text, leaving every other byte of it exactly as it was, for
 * the same reason [ChordProTags] does: what comes out of here is written back to the user's own file, so the
 * formatting they chose has to survive a language being picked in a dialog.
 *
 * ChordPro has no directive for the language of a song, so this is `{meta: language en}` — a custom metadata item,
 * which the spec leaves free for exactly this. The other spellings a file may use are read, see
 * [ChordProSyntax.language], but never written.
 */
object ChordProLanguages {

    /**
     * Rewrites the song's language directives to be exactly [codes], in one pass: the ones no longer wanted are
     * dropped wherever they sit, whichever spelling they use, and the new ones are written after the last language
     * the file already declares. A set that is already what the file says returns the text unchanged.
     */
    fun setLanguages(text: String, codes: List<String>): String {
        val wanted = codes.mapNotNull(ChordProSyntax::languageCode).distinct()
        val lines = ChordProSyntax.splitLines(text)
        val kept = mutableListOf<String>()
        val declared = mutableListOf<String>()
        lines.forEach { line ->
            val code = line.language()
            // A language the song is to keep stays on the line it is already written on, spelling included; one that
            // is not wanted any more, and a repeat of one already kept, are the lines that go.
            if (code == null || (code in wanted && code !in declared)) {
                kept += line
                code?.let { declared += it }
            }
        }
        val missing = wanted.filterNot { it in declared }
        if (missing.isEmpty() && kept.size == lines.size) return text
        kept.addAll(
            ChordProSyntax.metadataInsertionIndex(kept, ChordProSyntax.LANGUAGE_NAME),
            missing.map { "{meta: ${ChordProSyntax.LANGUAGE_NAME} $it}" },
        )
        return kept.joinToString("\n")
    }

    /**
     * The language [value] names as the library files it under, or null where it names none: the primary subtag,
     * lowercase, and the two letter code where the standard has one, which is the same normalization a directive
     * goes through on the way in.
     *
     * It is here so that a caller with a piece of text in hand — the search field of the language picker, where
     * somebody may well type `HUN` or `en-US` — can ask the question a file would be answered with, and find the
     * language under the one code everything above this module knows it by.
     */
    fun code(value: String) = ChordProSyntax.languageCode(value)

    /** The language of a line that is a language directive, null for every other line. */
    private fun String.language() = ChordProSyntax.matchDirective(trim())?.let { ChordProSyntax.language(it) }
}
