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

import com.pandulapeter.campfire.chordpro.ChordProEnvironments.END_OF_PREFIX
import com.pandulapeter.campfire.chordpro.ChordProEnvironments.START_OF_PREFIX
import com.pandulapeter.campfire.chordpro.ChordProEnvironments.endOfEnvironment
import com.pandulapeter.campfire.chordpro.ChordProEnvironments.endShortNames
import com.pandulapeter.campfire.chordpro.ChordProEnvironments.startOfEnvironment
import com.pandulapeter.campfire.chordpro.ChordProEnvironments.startShortNames
import com.pandulapeter.campfire.chordpro.ChordProHeaderLayout.metadataAliases

/** How a line is read as a directive and where the chords in brackets are, see [matchDirective]. */
internal object ChordProDirectives {

    private const val DIRECTIVE_OPEN = '{'
    private const val DIRECTIVE_CLOSE = '}'
    private const val DIRECTIVE_VALUE_SEPARATOR = ':'
    private const val BRACKET_OPEN = '['
    private const val BRACKET_CLOSE = ']'
    private const val CUSTOM_PREFIX = "x_"

    /**
     * Every directive name ChordPro defines, used to detect (and drop) selector suffixes such as `title-guitar` and to
     * accept a value separated from the name by whitespace alone.
     */
    private val knownNames = setOf(
        "title", "t", "subtitle", "st", "artist", "composer", "lyricist", "album", "year", "key", "capo", "tempo",
        "time", "duration", "transpose", "tag", "language", "lang", "meta", "chorus", "comment", "c", "comment_italic", "ci", "comment_box", "cb",
        "new_page", "np", "new_physical_page", "npp", "column_break", "colb", "new_song", "ns", "define", "chord",
        "image", "columns", "col", "highlight", "pagetype", "titles", "sorttitle", "arranger", "copyright", "grid", "g",
        "no_grid", "ng", "diagrams", "textfont", "tf", "textsize", "ts", "textcolour", "chordfont", "cf", "chordsize", "cs",
        "chordcolour", "tabfont", "tabsize", "tabcolour", "gridfont", "gridsize", "gridcolour", "titlefont", "titlesize",
        "titlecolour",
    )

    /** Matches a directive in one linear walk, which keeps malformed input cheap while the user types. */
    /**
     * [matchDirective] for a line of an environment ChordPro hands to another program, whose own syntax is full of
     * braces and `#`: only the `{end_of_…}` that closes it and a directive written with a colon are directives there,
     * since `{ c d e }` is LilyPond and not `{c: d e}`.
     */
    fun matchDelegatedDirective(trimmedLine: String): Directive? {
        val (name, valueStart) = walkDirective(trimmedLine) ?: return null
        val hasColon = valueStart > 0 && trimmedLine[valueStart - 1] == DIRECTIVE_VALUE_SEPARATOR
        return if (endOfEnvironment(name) != null || hasColon) matchDirective(trimmedLine) else null
    }

    fun matchDirective(trimmedLine: String): Directive? {
        val (name, valueStart) = walkDirective(trimmedLine) ?: return null
        return Directive(name, if (valueStart < 0) null else trimmedLine.substring(valueStart, trimmedLine.length - 1).trim())
    }

    /**
     * Where the value of the directive on [trimmedLine] starts: right after its colon, or at the first character after
     * the whitespace that separates it from a name written without one. Null for a line that is not a directive and
     * for one with no value. It is what splits a directive into its name and its value for the highlighter, which
     * cannot look for the colon, since a directive need not have one and a value may.
     */
    fun directiveValueStart(trimmedLine: String) = walkDirective(trimmedLine)?.second?.takeIf { it >= 0 }

    /**
     * Whether [trimmedLine] is a directive ChordPro defines, or a custom `x_` one. Unlike [matchDirective] it refuses
     * `{unknown: value}`, which is what tells a ChordPro file apart from a text that merely has braces in it.
     */
    fun isKnownDirective(trimmedLine: String) = walkDirective(trimmedLine)?.first?.let(::isKnownName) == true

    /**
     * The lowercase name of the directive on [trimmedLine] and the index its value starts at, -1 where it has none.
     *
     * ChordPro separates a value from the name with a colon "and/or whitespace", and the spec's own examples use the
     * second (`{start_of_verse Verse 1}`). Whitespace alone is only taken for a name the app knows, though: a line in
     * braces such as `{Verse 2}` has always been shown as the lyrics it is, and an unknown directive is dropped, so
     * reading it as one would take the user's text off the screen.
     */
    private fun walkDirective(trimmedLine: String): Pair<String, Int>? {
        val closeIndex = trimmedLine.length - 1
        if (closeIndex < 1 || trimmedLine[0] != DIRECTIVE_OPEN || trimmedLine[closeIndex] != DIRECTIVE_CLOSE) return null
        var index = 1
        while (index < closeIndex && trimmedLine[index].isWhitespace()) index++
        val nameStartIndex = index
        while (index < closeIndex && trimmedLine[index].isDirectiveNameCharacter) index++
        if (index == nameStartIndex) return null
        val nameEndIndex = index
        val writtenName = trimmedLine.substring(nameStartIndex, nameEndIndex).lowercase()
        // A negated selector holds wherever nothing matches the selector, which in Campfire is everywhere, so the
        // directive is read as the one it is written on. A `!` anywhere else is not part of a directive name.
        val name = if ('!' in writtenName) negatedSelectorBase(writtenName) ?: return null else writtenName
        while (index < closeIndex && trimmedLine[index].isWhitespace()) index++
        return when {
            index == closeIndex -> name to -1
            trimmedLine[index] == DIRECTIVE_VALUE_SEPARATOR -> name to index + 1
            index > nameEndIndex && isKnownName(name) -> name to index
            else -> null
        }
    }

    /** `title` for `title-guitar!`: the known directive a negated selector is written on, or null. */
    private fun negatedSelectorBase(name: String): String? {
        if (!name.endsWith('!') || name.count { it == '!' } != 1) return null
        val base = name.dropLast(1).substringBeforeLast('-', missingDelimiterValue = "")
        return base.takeIf { it.isNotEmpty() && isKnownName(it) }
    }

    /** Whether [name] is a directive ChordPro defines, a custom `x_` one or one of those with a selector suffix. */
    private fun isKnownName(name: String) = name in knownNames || startOfEnvironment(name) != null ||
            endOfEnvironment(name) != null || name.startsWith(CUSTOM_PREFIX) || hasSelectorSuffix(name)

    /** Every closed bracket pair from left to right; an unclosed opening bracket ends the walk. */
    fun brackets(line: String): List<Bracket> {
        val brackets = mutableListOf<Bracket>()
        var index = 0
        while (true) {
            val openIndex = line.indexOf(BRACKET_OPEN, index)
            if (openIndex < 0) break
            val closeIndex = line.indexOf(BRACKET_CLOSE, openIndex + 1)
            if (closeIndex < 0) break
            brackets += Bracket(openIndex..closeIndex, line.substring(openIndex + 1, closeIndex))
            index = closeIndex + 1
        }
        return brackets
    }

    /** Whether [line] contains at least one closed bracket pair. */
    fun hasBrackets(line: String): Boolean {
        val openIndex = line.indexOf(BRACKET_OPEN)
        return openIndex >= 0 && line.indexOf(BRACKET_CLOSE, openIndex + 1) >= 0
    }

    /** The characters an ASCII directive name may contain. */
    private val Char.isDirectiveNameCharacter get() = this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9' || this == '_' || this == '-' || this == '!'

    /** Every short directive name ChordPro defines, under its long one, see [canonicalDirective]. */
    private val longNames = metadataAliases +
        startShortNames.mapValues { (_, environment) -> START_OF_PREFIX + environment } +
        endShortNames.mapValues { (_, environment) -> END_OF_PREFIX + environment } +
        mapOf(
            "c" to "comment",
            "ci" to "comment_italic",
            "cb" to "comment_box",
            "np" to "new_page",
            "npp" to "new_physical_page",
            "colb" to "column_break",
            "col" to "columns",
            "ns" to "new_song",
            "g" to "grid",
            "ng" to "no_grid",
            "tf" to "textfont",
            "ts" to "textsize",
            "cf" to "chordfont",
            "cs" to "chordsize",
        )

    /**
     * [directive] as one spelling of it: its long name, and its value after a colon and a single space, or no value at
     * all for an empty one. Two lines with the same canonical spelling are read the same by [ChordProParser] (`{t:X}`,
     * `{title X}` and `{Title: X}` are all `{title: X}`), which is what an import asks when it holds two copies of a song
     * against each other. A name with a selector (`title-guitar`) is kept whole; a negated one has already been read as
     * the directive it is written on by [matchDirective].
     */
    fun canonicalDirective(directive: Directive): String {
        val name = longNames[directive.name] ?: directive.name
        val value = directive.value?.takeIf { it.isNotEmpty() }
        return if (value == null) "{$name}" else "{$name: $value}"
    }

    /**
     * Whether the value of the directive [name] is text the song shows — a comment, or the label of a section or of a
     * chorus recall — rather than a setting. Its brackets are then chords, as they are in a line of lyrics: that is
     * where an intro is written down as a row of chords, so the transposition moves them and the highlighter colours
     * them.
     */
    fun hasChordsInValue(name: String) = name in shownValueNames || startOfEnvironment(name) != null

    private val shownValueNames = setOf("chorus", "comment", "c", "comment_italic", "ci", "comment_box", "cb", "highlight")

    /**
     * True for names such as `title-guitar`: a known directive with a (non-negated) selector suffix, which Campfire
     * matches nothing against and drops. Environments are the exception, see [startOfEnvironment].
     */
    fun hasSelectorSuffix(name: String): Boolean {
        if (name.startsWith(START_OF_PREFIX) || name.startsWith(END_OF_PREFIX)) return false
        val separatorIndex = name.lastIndexOf('-')
        return separatorIndex > 0 && knownNames.contains(name.substring(0, separatorIndex))
    }

    data class Directive(val name: String, val value: String?)

    /** One closed bracket pair and its untrimmed content. */
    data class Bracket(val range: IntRange, val content: String)
}
