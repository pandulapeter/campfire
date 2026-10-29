/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.convert

import com.pandulapeter.campfire.chordpro.syntax.ChordProRowMarks.repeatCount

/**
 * The section headings chord and lyrics sites write, in the languages the app's users paste from. A heading is a whole
 * line: optionally wrapped in brackets or parentheses, optionally numbered before (`1. versszak`) or after (`Verse 1`,
 * `Parte 1 de 3`), optionally marked as repeated (`Chorus x2`, `[Chorus] (x2)`), and optionally ending in a colon.
 * Free text after the heading (`[Verse 1: Some Artist]`, the singer Genius names) is read only where the line is
 * wrapped or ends in a colon, since without either "Chorus of angels sing" would be one.
 */
internal object ChordSheetHeadings {
    /**
     * A recognized heading. [label] is the line without its wrapping and trailing colon, [type] the ChordPro section it
     * opens (`verse`, `chorus` or `bridge`), null for one written as a comment. [isAmbiguous] is a bare word that is as
     * likely sung, which only the lines around it can decide.
     */
    data class Heading(val label: String, val type: String?, val isAmbiguous: Boolean)

    fun parse(line: String): Heading? {
        var text = line.trim()
        val endsWithColon = text.endsWith(':')
        text = text.removeSuffix(":").trim()
        val wrapped = bracketed.matchEntire(text) ?: parenthesized.matchEntire(text)
        val inner = wrapped?.groupValues?.get(1)?.trim()?.removeSuffix(":")?.trim() ?: text
        val match = heading.matchEntire(inner) ?: return null
        val tail = match.groupValues[3]
        if (tail.isNotEmpty() && wrapped == null && !endsWithColon) return null
        val word = match.groupValues[1].lowercase()
        val isBare = inner.length == match.groupValues[1].length
        val repeatAfter = wrapped?.groupValues?.get(2).orEmpty()
        return Heading(
            label = if (repeatAfter.isEmpty()) inner else "$inner $repeatAfter",
            type = types[word],
            isAmbiguous = isBare && wrapped == null && !endsWithColon && word in sung,
        )
    }

    /**
     * The type of a [label] known to be one, as Campfire's export prints its sections' labels, by the heading word it opens
     * with whatever follows it (`Chorus (twice)`, `Bridge \u2014 build`): `pre-chorus` for the editor's own kind, which
     * a site's heading is read as a bridge for, the [Heading.type] otherwise, and null for a label opening with none.
     */
    fun labelType(label: String): String? {
        val text = label.trim()
        fun opensWith(word: String) = text.startsWith(word, ignoreCase = true) && text.getOrNull(word.length)?.isLetter() != true
        if (preChorus.any(::opensWith)) return PRE_CHORUS
        return labelWords.firstOrNull(::opensWith)?.let { types[it.lowercase()] }
    }

    private const val PRE_CHORUS = "pre-chorus"

    /** Every pre-chorus heading, the app's own label in each of its languages included. */
    private val preChorus = listOf(
        "Pre-Chorus", "Pre Chorus", "Prechorus", "Pr\u00e9-Refr\u00e3o", "Pre-Coro", "Pre-Estribillo", "Preestribillo", "Pr\u00e9-refrain",
        "Pre-refrain", "Pre-ritornello", "Pre-refren", "Pre-refr\u00e9n", "\u05d8\u05e8\u05d5\u05dd\u05be\u05e4\u05d6\u05de\u05d5\u05df",
    )

    private val verse = listOf(
        "Verse", "Versszak", "Vsz.", "Vsz", "Strophe", "Strofa", "Strof\u0103", "Zwrotka", "Couplet", "Estrofa", "Estrofe", "Verso", "\u05d1\u05d9\u05ea",
    )
    private val chorus = listOf(
        "Chorus", "Refrain", "Refr\u00e9n", "Ref.", "Ref", "R.", "R", "Ritornello", "Refren", "Refrein", "Refr\u00e3o", "Refrao", "Coro",
        "Estribillo", "Hook", "\u05e4\u05d6\u05de\u05d5\u05df",
    )
    private val bridge = listOf(
        "Bridge", "Post-Chorus", "Post Chorus", "Postchorus", "Puente", "Ponte", "Pont", "Brug",
        "\u00c1tk\u00f6t\u00e9s", "\u00c1tvezet\u00e9s", "\u05d2\u05e9\u05e8",
    ) + preChorus
    private val untyped = listOf(
        "Intro", "Outro", "Solo", "Interlude", "Instrumental", "Instrumental break", "Tag", "Coda", "Ending", "Break", "Breakdown", "Vamp",
        "Turnaround", "Parte", "Sz\u00f3l\u00f3", "Assolo", "\u05e4\u05ea\u05d9\u05d7\u05d4", "\u05e1\u05d9\u05d5\u05dd", "\u05e1\u05d5\u05dc\u05d5",
    )

    /** Words that are also sung, which make a heading on their own only with brackets, a colon, or a chord row or blank line after them. */
    private val sung = setOf("r", "coro", "parte", "tag", "break", "ending", "hook", "verso", "pont", "ponte", "\u05d1\u05d9\u05ea", "\u05d2\u05e9\u05e8")

    private val types = buildMap<String, String?> {
        verse.forEach { put(it.lowercase(), "verse") }
        chorus.forEach { put(it.lowercase(), "chorus") }
        bridge.forEach { put(it.lowercase(), "bridge") }
        untyped.forEach { put(it.lowercase(), null) }
    }

    private val labelWords = (verse + chorus + bridge + untyped).sortedByDescending { it.length }

    /** The longest word is tried first, so that "Instrumental break" is one word rather than "Instrumental" with a tail. */
    private val heading = Regex(
        "^(?:[0-9]+\\. *)?(" + (verse + chorus + bridge + untyped).sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) } + ")" +
            "(?: *[0-9]+(?: +de +[0-9]+)?)?(?: *(${repeatCount.pattern}))?(?:(?: *: *| +[-\u2013] +)(.+))?$",
        RegexOption.IGNORE_CASE,
    )
    private val bracketed = Regex("^\\[([^\\[\\]]+)\\](?: *(${repeatCount.pattern}))?$", RegexOption.IGNORE_CASE)
    private val parenthesized = Regex("^\\(([^()]+)\\)(?: *(${repeatCount.pattern}))?$", RegexOption.IGNORE_CASE)
}
