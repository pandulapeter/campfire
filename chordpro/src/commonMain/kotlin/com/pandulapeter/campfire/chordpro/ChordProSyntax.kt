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
 * The low level syntax rules shared by [ChordProParser], [ChordProSerializer], [ChordProTransposer],
 * [ChordProNotation] and [ChordProTabWrapper].
 */
internal object ChordProSyntax {



    private val labelAttributeRegex = Regex("(?:^|\\s)label\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')")
    private val attributeRegex = Regex("\\s*[A-Za-z_][A-Za-z0-9_-]*\\s*=\\s*(?:\"[^\"]*\"|'[^']*')")
    private val whitespaceRegex = Regex("\\s+")
    private val barLines = setOf("|", "||", "|.", "|:", ":|", ":|:")
    private val voltaRegex = Regex(":?\\|\\d+>?")

    const val START_OF_PREFIX = "start_of_"
    const val END_OF_PREFIX = "end_of_"
    const val GRID_CELL_CHORD_SEPARATOR = "~"

    /** The name of the `{tag}` directive, which is also the `{meta}` key that spells the same thing. */
    const val TAG_NAME = "tag"

    /** The `{meta}` key a song's language is written under, since ChordPro defines no directive of its own for it. */
    const val LANGUAGE_NAME = "language"
    private const val LANGUAGE_SHORT_NAME = "lang"
    private const val META = "meta"
    private const val SOURCE_COMMENT = "#"
    private const val STAFF_DASH = '-'
    private const val MINIMUM_STAFF_DASH_COUNT = 3
    private const val DIRECTIVE_OPEN = '{'
    private const val DIRECTIVE_CLOSE = '}'
    private const val DIRECTIVE_VALUE_SEPARATOR = ':'
    private const val BRACKET_OPEN = '['
    private const val BRACKET_CLOSE = ']'
    private const val CUSTOM_PREFIX = "x_"

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

    /** Splits into lines accepting both `\r\n` and `\n`; a trailing newline does not create an extra line. */
    fun splitLines(text: String): List<String> {
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
        val lines = normalized.split('\n')
        return if (lines.size > 1 && lines.last().isEmpty()) lines.subList(0, lines.size - 1) else lines
    }

    /**
     * The line separator [text] is written with: CRLF where any line of it ends that way, LF otherwise. It is one
     * answer for the whole file, so a file mixing CR-only or LF endings with CRLF ones comes out of an edit written with
     * CRLF throughout, which is the separator such a file was most likely meant to have.
     */
    fun lineSeparatorOf(text: String) = if (text.contains("\r\n")) "\r\n" else "\n"

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

    /** Matches a directive in one linear walk, which keeps malformed input cheap while the user types. */
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
        val name = trimmedLine.substring(nameStartIndex, nameEndIndex).lowercase()
        while (index < closeIndex && trimmedLine[index].isWhitespace()) index++
        return when {
            index == closeIndex -> name to -1
            trimmedLine[index] == DIRECTIVE_VALUE_SEPARATOR -> name to index + 1
            index > nameEndIndex && isKnownName(name) -> name to index
            else -> null
        }
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
    private val Char.isDirectiveNameCharacter get() = this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9' || this == '_' || this == '-'

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
     * The names the spec defines `{meta: name value}` to mean exactly what the standalone `{name: value}` means, of the
     * ones the model has a field for; the tag and the language are read by [tag] and [language].
     */
    private val standardMetaNames = setOf(
        "title", "subtitle", "artist", "composer", "lyricist", "album", "year", "key", "capo", "tempo", "time", "duration",
    )

    /**
     * The standalone directive a `{meta}` one stands for (`{meta: title Amazing Grace}` is `{title: Amazing Grace}`), or
     * null for every other directive, the custom metadata a `{meta}` may carry included. The name is matched ignoring
     * case, like every directive name.
     */
    fun standardMeta(directive: Directive): Directive? {
        if (directive.name != META) return null
        val value = directive.value?.trim() ?: return null
        val name = value.substringBefore(' ').trim().lowercase()
        return if (name in standardMetaNames) Directive(name, value.substringAfter(' ', missingDelimiterValue = "").trim()) else null
    }

    /**
     * Equal for exactly the strings `equals(ignoreCase = true)` calls equal, char by char, so that it can key a hash set.
     * Not `lowercase()`, which turns a dotted capital I into two characters that no longer match the plain i the
     * comparison takes it for.
     */
    fun caseInsensitiveKey(value: String) = CharArray(value.length) { value[it].uppercaseChar().lowercaseChar() }.concatToString()

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
     * and the `{meta}` ones (`{meta: language en}`, `{meta: title …}`) fold into the long one, so that a file writing a
     * directive one way and a toolbar writing it another are understood to be talking about the same thing. Null for
     * everything else, the directives that make up the body and the custom metadata a song may carry included.
     */
    fun metadataKind(directive: Directive): String? = when {
        isTagMeta(directive) -> TAG_NAME
        isLanguageMeta(directive) -> LANGUAGE_NAME
        else -> {
            val name = standardMeta(directive)?.name ?: directive.name
            (metadataAliases[name] ?: name).takeIf { it in metadataOrder }
        }
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

    /**
     * The directives [ChordProParser] makes a block of their own out of — a comment, a break, a chorus recall —
     * and which therefore cut whatever section they stand in into two, a run of tablature included.
     */
    val blockNames = setOf(
        "chorus", "comment", "c", "comment_italic", "ci", "comment_box", "cb", "new_page", "np", "new_physical_page",
        "npp", "column_break", "colb",
    )

    private val bodyNames = blockNames + setOf("new_song", "ns")

    /**
     * The environments ChordPro hands to another program — ABC and LilyPond notation, SVG, a block of preformatted
     * text. Their lines are that program's input rather than lyrics: `[CEG]` is an ABC chord of three notes, and moving
     * it as a ChordPro chord would corrupt music nobody asked to change. They are kept line for line and nothing in
     * them is a chord.
     */
    val delegateEnvironments = setOf("abc", "ly", "svg", "textblock")

    /**
     * The label an environment directive gives its section: the whole value (`{sov: Verse 1}`), or its `label`
     * attribute where the value is written as attributes (`{sov label="Verse 1"}`, in either quotes). A value made of
     * attributes that has no label (`{start_of_grid shape="1+4x2+4"}`) gives none, rather than showing the attributes
     * as a heading. An empty label is null.
     */
    fun label(value: String?): String? {
        val trimmedValue = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (!isAttributes(trimmedValue)) return trimmedValue
        val match = labelAttributeRegex.find(trimmedValue) ?: return null
        return match.groupValues[1].ifEmpty { match.groupValues[2] }.takeIf { it.isNotEmpty() }
    }

    /**
     * Whether [trimmedValue] is nothing but `name="value"` attributes. It is matched one attribute at a time rather than
     * with a single repeated group, which the JVM's regex engine matches recursively and overflows the stack on for a
     * long enough line.
     */
    private fun isAttributes(trimmedValue: String): Boolean {
        var index = 0
        while (index < trimmedValue.length) {
            index = (attributeRegex.matchAt(trimmedValue, index) ?: return false).range.last + 1
        }
        return true
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

    /**
     * Splits a grid line into tokens. ChordPro puts whatever comes before the first bar line in the left margin and
     * whatever follows the last one in the right margin, so on a line that has a bar both are text: a margin label
     * such as `A` or `Coda` names a part of the song, and taking it for a chord would transpose it. A `/` marks where a
     * chord is played and is not one either.
     */
    fun parseGridTokens(trimmedLine: String): List<GridToken> {
        val words = trimmedLine.split(whitespaceRegex).filter { it.isNotEmpty() }
        val firstBarIndex = words.indexOfFirst { isBar(it) }
        val lastBarIndex = words.indexOfLast { isBar(it) }
        return words.mapIndexed { index, word ->
            when {
                firstBarIndex >= 0 && (index < firstBarIndex || index > lastBarIndex) -> GridToken.Text(word)
                isBar(word) -> GridToken.Bar(word)
                word == "." -> GridToken.Beat
                word == "%" || word == "%%" -> GridToken.Repeat(word)
                word == "/" -> GridToken.Text(word)
                else -> GridToken.Chord(word)
            }
        }
    }

    /** The bar lines of a grid, the repeats and the voltas (`|1`, `:|2`, `:|2>`) included. */
    fun isBar(word: String) = word in barLines || voltaRegex.matches(word)

    /**
     * The chords of one grid cell: ChordPro writes several chords into a cell by joining them with a `~`, and each
     * of them is a chord of its own to transpose or respell.
     */
    fun cellChords(cell: String) = cell.split(GRID_CELL_CHORD_SEPARATOR)

    /**
     * A tablature line: enough dashes to be a staff, and made mostly of the characters a staff is made of. The letters
     * of a technique (`h`, `p`, `x`, …) and the string name in front of the line are the minority that is allowed. It
     * is what tells the staff of a `{start_of_tab}` environment from the chord names above it and the notes around
     * it, for the transposer (which moves frets on the one and chord names on the other) and for the viewer (which
     * cuts a staff at its columns and never a line of prose).
     */
    fun isStaffLine(line: String): Boolean {
        var dashCount = 0
        var staffCharacterCount = 0
        var otherCharacterCount = 0
        line.forEach { character ->
            when {
                character == STAFF_DASH -> {
                    dashCount++
                    staffCharacterCount++
                }

                character == '|' || character.isDigit() -> staffCharacterCount++
                character.isWhitespace() -> Unit
                else -> otherCharacterCount++
            }
        }
        return dashCount >= MINIMUM_STAFF_DASH_COUNT && staffCharacterCount >= otherCharacterCount
    }

    data class Directive(val name: String, val value: String?)

    /** One closed bracket pair and its untrimmed content. */
    data class Bracket(val range: IntRange, val content: String)
}
