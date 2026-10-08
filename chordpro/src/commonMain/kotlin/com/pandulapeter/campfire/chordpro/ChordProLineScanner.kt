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

import com.pandulapeter.campfire.chordpro.ChordProVocabulary.SOURCE_COMMENT

/**
 * The one walk over the lines of a ChordPro text that every reader of raw text shares: which environment each line
 * stands in, whether it is a `#` source comment and which directive it is, all decided by the parser's rule.
 *
 * A `{start_of_…}` opens its environment and any `{end_of_…}` closes the one that is open, whichever environment it
 * names, since the parser reads every line after either as an ordinary one. Inside an environment handed to another
 * program ([ChordProEnvironments.delegateEnvironments]) a `#` and a brace are that program's syntax, so only what
 * [ChordProDirectives.matchDelegatedDirective] accepts is a directive there, and one the file never closes takes the
 * rest of the text.
 */
internal object ChordProLineScanner {

    /** One line of the text and how the parser reads it. */
    class ScannedLine(
        val index: Int,
        val raw: String,
        val trimmed: String,
        /** The lowercase environment open *before* this line ("tab", "grid", "abc", …), or null. */
        val environment: String?,
        /** Whether [environment] is one handed to another program. */
        val isDelegated: Boolean,
        /** A `#` line outside an environment handed to another program, which never reaches the song. */
        val isSourceComment: Boolean,
        /** The directive this line is, read by the rule of [environment]; null for a source comment and any other line. */
        val directive: ChordProDirectives.Directive?,
        /** The lowercase environment open *after* this line, which only a directive line changes. */
        val environmentAfter: String?,
    ) {
        /** Whether [environmentAfter] is one handed to another program. */
        val isDelegatedAfter get() = environmentAfter in ChordProEnvironments.delegateEnvironments
    }

    /** Every line of [text], split by [ChordProLines.splitLines]. */
    fun scan(text: String) = scan(ChordProLines.splitLines(text))

    /** Every line of [lines], read lazily, so a caller that stops early does not match the rest. */
    fun scan(lines: List<String>): Sequence<ScannedLine> = sequence {
        var environment: String? = null
        lines.forEachIndexed { index, raw ->
            val trimmed = raw.trim()
            val isDelegated = environment in ChordProEnvironments.delegateEnvironments
            val isSourceComment = !isDelegated && trimmed.startsWith(SOURCE_COMMENT)
            val directive = when {
                isSourceComment -> null
                isDelegated -> ChordProDirectives.matchDelegatedDirective(trimmed)
                else -> ChordProDirectives.matchDirective(trimmed)
            }
            val environmentAfter = if (directive == null) environment else environmentAfter(directive, environment)
            yield(
                ScannedLine(
                    index = index,
                    raw = raw,
                    trimmed = trimmed,
                    environment = environment,
                    isDelegated = isDelegated,
                    isSourceComment = isSourceComment,
                    directive = directive,
                    environmentAfter = environmentAfter,
                )
            )
            environment = environmentAfter
        }
    }

    /** The environment open after [directive], [environment] being the one open before it (see [scan]). */
    private fun environmentAfter(directive: ChordProDirectives.Directive, environment: String?): String? {
        ChordProEnvironments.startOfEnvironment(directive.name)?.let { return it.lowercase() }
        if (ChordProEnvironments.endOfEnvironment(directive.name) != null) return null
        return environment
    }
}
