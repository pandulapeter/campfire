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

    /** The `{meta}` key a song's language is written under, since ChordPro defines no directive of its own for it. */
    const val LANGUAGE_NAME = "language"
    private const val LANGUAGE_SHORT_NAME = "lang"
    private const val META = "meta"
    private const val SOURCE_COMMENT = "#"

    /**
     * The two ISO codes that mean "there is no language here" — undetermined and no linguistic content. They say
     * exactly what a file with no language directive says, so they are read as that rather than as a language of
     * their own, which also leaves `und` free to stand for "declares none" everywhere above this module.
     */
    private val absentLanguageCodes = setOf("und", "zxx")

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
        "time", "duration", "transpose", "tag", "language", "lang", "meta", "chorus", "comment", "c", "comment_italic", "ci", "comment_box", "cb",
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

    /**
     * The language a directive declares, or null if it is not one. ChordPro defines no directive for it, so what the
     * app writes is `{meta: language en}`; `{meta: lang en}` and the bare `{language: en}` / `{lang: en}` that a hand
     * written file may carry are read as the same thing. What comes back is normalized, see [languageCode].
     */
    fun language(directive: Directive): String? = when (directive.name) {
        LANGUAGE_NAME, LANGUAGE_SHORT_NAME -> directive.value
        META -> directive.value?.trim()
            ?.takeIf { it.substringBefore(' ').trim().isLanguageKey }
            ?.substringAfter(' ', missingDelimiterValue = "")

        else -> null
    }?.let(::languageCode)

    /**
     * A language value as the library files it under: the primary subtag, lowercase, and the two letter code where
     * the standard has one for it.
     *
     * The region is cut off here, where the dialect is decided, for two reasons — `en-US` next to `en` would be a
     * filter group of its own for what is the same language to a song book, and the platforms disagree about what to
     * call one, a browser saying "American English" where the JDK says "English". A three letter ISO 639-2 code is
     * folded into its ISO 639-1 equivalent for the first of those reasons and one more, see
     * [ChordProLanguageCodes]; one that has no such equivalent, which is most of what 639-2 adds, is kept as it is.
     */
    fun languageCode(value: String) = value.trim()
        .substringBefore('-')
        .substringBefore('_')
        .lowercase()
        .let { ChordProLanguageCodes.twoLetterEquivalents[it] ?: it }
        .takeIf { it.isNotEmpty() && it !in absentLanguageCodes }

    /** True for a `{meta}` directive whose key names the language, whatever its value then turns out to be worth. */
    fun isLanguageMeta(directive: Directive) = directive.name == META && directive.value?.trim()?.substringBefore(' ')?.trim()?.isLanguageKey == true

    /** The same for a `{meta}` directive whose key names a tag, which is the spelling ChordPro documents next to `{tag}`. */
    private fun isTagMeta(directive: Directive) = directive.name == META &&
            directive.value?.trim()?.substringBefore(' ')?.trim()?.equals(TAG_NAME, ignoreCase = true) == true

    private val String.isLanguageKey get() = equals(LANGUAGE_NAME, ignoreCase = true) || equals(LANGUAGE_SHORT_NAME, ignoreCase = true)

    /**
     * The directives a song describes itself with, in the order a header reads best in: what the song is called, who
     * made it, what record it came on, and how it is played, with the two repeatable ones at the end. It is what
     * decides where a directive added to a file lands, see [metadataInsertionIndex].
     */
    private val metadataOrder = listOf(
        "title", "subtitle", "artist", "composer", "lyricist", "album", "year", "key", "capo", "tempo", "time",
        "duration", TAG_NAME, LANGUAGE_NAME,
    )

    /**
     * The metadata directive a line declares, under the single name the app knows it by: the short spellings (`{t}`)
     * and the `{meta}` ones (`{meta: language en}`) fold into the long one, so that a file writing a directive one
     * way and a toolbar writing it another are understood to be talking about the same thing. Null for everything
     * else, the directives that make up the body and the custom metadata a song may carry included.
     */
    fun metadataKind(directive: Directive): String? = when {
        isTagMeta(directive) -> TAG_NAME
        isLanguageMeta(directive) -> LANGUAGE_NAME
        else -> (metadataAliases[directive.name] ?: directive.name).takeIf { it in metadataOrder }
    }

    private val metadataAliases = mapOf(
        "t" to "title",
        "st" to "subtitle",
        LANGUAGE_SHORT_NAME to LANGUAGE_NAME,
    )

    /**
     * Where a directive of kind [name] goes when one is added to a file the user wrote.
     *
     * Right after the last directive of its own kind, wherever that is, so that the tags of a song stay together and
     * so do its languages. A kind the file does not declare yet goes into the block of directives the file opens
     * with, after the last one that comes before it in [metadataOrder]: a header written in that order stays in it,
     * and one the user has arranged some other way is left exactly as it is, since nothing already written is ever
     * moved. A file that opens with content rather than directives gets it on a line of its own above everything.
     */
    fun metadataInsertionIndex(lines: List<String>, name: String): Int {
        lines.indexOfLast { line -> matchDirective(line.trim())?.let(::metadataKind) == name }.takeIf { it >= 0 }?.let { return it + 1 }
        val rank = metadataOrder.indexOf(name).takeIf { it >= 0 } ?: metadataOrder.size
        var headerIndex = -1
        var insertionIndex = -1
        for ((index, line) in lines.withIndex()) {
            val trimmedLine = line.trim()
            if (trimmedLine.isEmpty() || trimmedLine.startsWith(SOURCE_COMMENT)) continue
            val directive = matchDirective(trimmedLine) ?: break
            if (!directive.isMetadata) break
            if (headerIndex < 0) headerIndex = index
            val otherRank = metadataKind(directive)?.let(metadataOrder::indexOf) ?: continue
            if (otherRank < rank) insertionIndex = index + 1
        }
        return if (insertionIndex >= 0) insertionIndex else headerIndex.coerceAtLeast(0)
    }

    /** True for the directives a song is described by, as opposed to the ones that make up its body. */
    private val Directive.isMetadata
        get() = startOfEnvironment(name) == null && endOfEnvironment(name) == null && name !in bodyNames

    private val bodyNames = setOf(
        "chorus", "comment", "c", "comment_italic", "ci", "comment_box", "cb", "new_page", "np", "new_physical_page",
        "npp", "column_break", "colb", "new_song", "ns",
    )

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
