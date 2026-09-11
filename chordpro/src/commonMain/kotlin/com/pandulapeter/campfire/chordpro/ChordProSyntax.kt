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

import com.pandulapeter.campfire.chordpro.model.GridToken

/**
 * The low level syntax rules shared by [ChordProParser], [ChordProSerializer], [ChordProTransposer] and
 * [ChordProNotation].
 */
internal object ChordProSyntax {

    val directiveRegex = Regex("^\\{\\s*([\\w-]+)\\s*(?::\\s*(.*?))?\\s*\\}$")
    val chordRegex = Regex("\\[(.*?)]")

    /**
     * A whole word that is a chord name: a note (German `H` included), an optional accidental, a quality, extensions
     * and a bass note. It is the answer to "is this a chord and not a word that happens to start with a letter", which
     * is what a row of chord names above a tab and [ChordProNotation] both need; the transposer does not use it,
     * because there the brackets or the grid have already said that a chord is what this is.
     */
    val chordNameRegex = Regex(
        "[A-H][#b♯♭]?(?:maj|min|dim|aug|sus|add|m|M|\\+|°|ø)?[0-9]*(?:(?:maj|min|dim|aug|sus|add|[#b♯♭])[0-9]*)*(?:/[A-H][#b♯♭]?)?"
    )

    private val labelAttributeRegex = Regex("label\\s*=\\s*\"([^\"]*)\"")
    private val whitespaceRegex = Regex("\\s+")

    const val START_OF_PREFIX = "start_of_"
    const val END_OF_PREFIX = "end_of_"

    /** The name of the `{tag}` directive, which is also the `{meta}` key that spells the same thing. */
    const val TAG_NAME = "tag"
    private const val META = "meta"

    private val startShortNames = mapOf(
        "soc" to "chorus",
        "sov" to "verse",
        "sob" to "bridge",
        "sot" to "tab",
        "sog" to "grid",
    )
    private val endShortNames = mapOf(
        "eoc" to "chorus",
        "eov" to "verse",
        "eob" to "bridge",
        "eot" to "tab",
        "eog" to "grid",
    )

    /** Every directive name the parser reacts to, used to detect (and drop) selector suffixes such as `title-guitar`. */
    private val knownNames = setOf(
        "title", "t", "subtitle", "st", "artist", "composer", "lyricist", "album", "year", "key", "capo", "tempo",
        "time", "duration", "transpose", "tag", "meta", "chorus", "comment", "c", "comment_italic", "ci", "comment_box", "cb",
        "new_page", "np", "new_physical_page", "npp", "column_break", "colb", "new_song", "ns", "define", "chord",
        "image", "columns", "col", "highlight", "pagetype", "titles",
    )

    /** Splits into lines accepting both `\r\n` and `\n`; a trailing newline does not create an extra line. */
    fun splitLines(text: String): List<String> {
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
        val lines = normalized.split('\n')
        return if (lines.size > 1 && lines.last().isEmpty()) lines.subList(0, lines.size - 1) else lines
    }

    /** Matches a directive line, or returns null for content. The name comes back lowercase, the value trimmed. */
    fun matchDirective(trimmedLine: String): Directive? {
        val match = directiveRegex.matchEntire(trimmedLine) ?: return null
        val name = match.groupValues[1].lowercase()
        val value = if (match.groupValues.size > 2) match.groups[2]?.value else null
        return Directive(name = name, value = value)
    }

    /**
     * The tag a directive carries, or null if it is not a tag directive. ChordPro documents `{tag: Needs study}` and
     * `{meta: tag Needs study}` as the same thing, and both are read here; one tag per directive, repeated as many
     * times as the song has tags. The value is taken whole, since the spec calls a tag arbitrary text.
     */
    fun tag(directive: Directive): String? = when (directive.name) {
        TAG_NAME -> directive.value?.trim()
        META -> directive.value?.trim()
            ?.takeIf { it.substringBefore(' ').trim().equals(TAG_NAME, ignoreCase = true) }
            ?.substringAfter(' ', missingDelimiterValue = "")
            ?.trim()

        else -> null
    }?.takeIf { it.isNotEmpty() }

    /** `label="Verse 1"` wins over the raw value; an empty value becomes null. */
    fun label(value: String?): String? {
        if (value == null) return null
        labelAttributeRegex.find(value)?.let { return it.groupValues[1].takeIf { label -> label.isNotEmpty() } }
        return value.trim().takeIf { it.isNotEmpty() }
    }

    /** True for names such as `title-guitar`: a known directive with a selector suffix, which is out of scope. */
    fun hasSelectorSuffix(name: String): Boolean {
        if (name.startsWith(START_OF_PREFIX) || name.startsWith(END_OF_PREFIX)) return false
        val separatorIndex = name.lastIndexOf('-')
        return separatorIndex > 0 && knownNames.contains(name.substring(0, separatorIndex))
    }

    /** The environment name of a `{start_of_x}` / `{soc}` style directive, or null if this is not one. */
    fun startOfEnvironment(name: String) = startShortNames[name]
        ?: name.takeIf { it.startsWith(START_OF_PREFIX) && it.length > START_OF_PREFIX.length }?.substring(START_OF_PREFIX.length)

    /** The environment name of an `{end_of_x}` / `{eoc}` style directive, or null if this is not one. */
    fun endOfEnvironment(name: String) = endShortNames[name]
        ?: name.takeIf { it.startsWith(END_OF_PREFIX) && it.length > END_OF_PREFIX.length }?.substring(END_OF_PREFIX.length)

    /** Splits a grid line into tokens: everything after the last bar token becomes free text. */
    fun parseGridTokens(trimmedLine: String): List<GridToken> {
        val words = trimmedLine.split(whitespaceRegex).filter { it.isNotEmpty() }
        val lastBarIndex = words.indexOfLast { isBar(it) }
        return words.mapIndexed { index, word ->
            when {
                lastBarIndex >= 0 && index > lastBarIndex -> GridToken.Text(word)
                isBar(word) -> GridToken.Bar(word)
                word == "." -> GridToken.Beat
                word == "%" || word == "%%" -> GridToken.Repeat(word)
                else -> GridToken.Chord(word)
            }
        }
    }

    fun isBar(word: String) = word == "|" || word == "||" || word == "|:" || word == ":|" || word == "|."

    data class Directive(val name: String, val value: String?)
}
