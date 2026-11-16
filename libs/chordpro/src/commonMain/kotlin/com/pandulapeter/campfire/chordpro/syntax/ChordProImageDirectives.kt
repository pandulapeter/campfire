/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.syntax

import com.pandulapeter.campfire.chordpro.syntax.ChordProVocabulary.SOURCE_COMMENT

/**
 * How the value of an `{image}` directive is split into its attributes, and which of them name a file.
 *
 * ChordPro writes `key=value` pairs separated by whitespace, a value holding whitespace quoted with `"` or `'`, and
 * still reads its older short form, whose first bare word is the file (`{image: score.png width=200}`). A bare word
 * that is a flag (`center`, `border`) is that flag; any other bare word after the file is no attribute at all. A quote
 * nothing closes runs to the end of the value, so a half-typed line is read rather than refused.
 */
internal object ChordProImageDirectives {

    const val IMAGE = "image"
    const val SRC = "src"
    const val ID = "id"

    private val flags = setOf("center", "border")

    /**
     * One attribute as written. [key] is lowercase, or null for a bare word. [value] is the text between the quotes,
     * which [valueRange] spans in the value it was read from, and [writtenRange] spans the value with its quotes.
     */
    class Attribute(
        val key: String?,
        val value: String,
        val valueRange: IntRange,
        val writtenRange: IntRange,
    )

    /** Whether [name] (lowercase) is an image directive, with a selector suffix (`image-piano`) or without. */
    fun isImageName(name: String) = name == IMAGE ||
        (ChordProDirectives.hasSelectorSuffix(name) && name.substringBeforeLast('-') == IMAGE)

    /** Every attribute [value] writes, in order. Never throws, whatever it holds. */
    fun attributes(value: String): List<Attribute> {
        val attributes = mutableListOf<Attribute>()
        var index = 0
        while (index < value.length) {
            while (index < value.length && value[index].isWhitespace()) index++
            if (index >= value.length) break
            if (value[index].isQuote) {
                val (range, written) = quoted(value, index)
                attributes += Attribute(null, value.substring(range), range, written)
                index = written.last + 1
                continue
            }
            val keyStart = index
            while (index < value.length && !value[index].isWhitespace() && value[index] != '=') index++
            val word = value.substring(keyStart, index)
            if (index >= value.length || value[index] != '=') {
                attributes += Attribute(null, word, keyStart until index, keyStart until index)
                continue
            }
            index++
            if (index < value.length && value[index].isQuote) {
                val (range, written) = quoted(value, index)
                attributes += Attribute(word.lowercase(), value.substring(range), range, written)
                index = written.last + 1
            } else {
                val valueStart = index
                while (index < value.length && !value[index].isWhitespace()) index++
                attributes += Attribute(word.lowercase(), value.substring(valueStart, index), valueStart until index, valueStart until index)
            }
        }
        return attributes
    }

    /**
     * The attributes that name a file: every `src=`, or where there is none, the first bare word that is not a flag
     * (the short form). Usually one; a file repeating `src=` names each.
     */
    fun sources(attributes: List<Attribute>): List<Attribute> = attributes.filter { it.key == SRC }.ifEmpty {
        listOfNotNull(attributes.firstOrNull { it.key == null && !it.isFlag })
    }

    /**
     * [attributes] as the model keeps them: keys lowercase, the first of a repeated key winning, a flag with an empty
     * value, and the short form's file as [SRC]. Other bare words are left out.
     */
    fun attributeMap(attributes: List<Attribute>): Map<String, String> {
        val legacySource = sources(attributes).firstOrNull()?.takeIf { it.key == null }
        val map = LinkedHashMap<String, String>()
        attributes.forEach { attribute ->
            when {
                attribute === legacySource -> map.getOrPut(SRC) { attribute.value }
                attribute.key != null -> map.getOrPut(attribute.key) { attribute.value }
                attribute.isFlag -> map.getOrPut(attribute.value.lowercase()) { "" }
            }
        }
        return map
    }

    /**
     * The file attributes of the image directive on [rawLine], their ranges moved onto [rawLine], read as generously as
     * a line can be: in any environment, with a selector suffix, and behind the `#` of a source comment. Empty for any
     * other line. This decides which pictures a song keeps, so a line the parser does not draw still counts: a picture
     * must not be deleted because its line was commented out for a moment, or sits where another program reads it.
     */
    fun sourcesOn(rawLine: String): List<Attribute> {
        var start = 0
        while (start < rawLine.length && (rawLine[start].isWhitespace() || rawLine.startsWith(SOURCE_COMMENT, start))) start++
        if (start >= rawLine.length || rawLine[start] != '{') return emptyList()
        val directiveLine = rawLine.substring(start).trimEnd()
        val directive = ChordProDirectives.matchDirective(directiveLine) ?: return emptyList()
        if (!isImageName(directive.name)) return emptyList()
        val valueStart = ChordProDirectives.directiveValueStart(directiveLine) ?: return emptyList()
        val offset = start + valueStart
        return sources(attributes(directiveLine.substring(valueStart, directiveLine.length - 1)))
            .filter { it.value.isNotEmpty() }
            .map { Attribute(it.key, it.value, it.valueRange.shift(offset), it.writtenRange.shift(offset)) }
    }

    /** [value] as an attribute value: bare where it can be, else quoted with a quote it does not hold. */
    fun written(value: String): String {
        val needsQuotes = value.isEmpty() || value.any { it.isWhitespace() || it == '=' || it.isQuote }
        if (!needsQuotes) return value
        val quote = if ('"' in value) '\'' else '"'
        return "$quote$value$quote"
    }

    /** The content of the quote opening at [start] and the quote with its quotes; one nothing closes runs to the end. */
    private fun quoted(value: String, start: Int): Pair<IntRange, IntRange> {
        val close = value.indexOf(value[start], start + 1)
        return if (close < 0) (start + 1 until value.length) to (start until value.length) else (start + 1 until close) to (start..close)
    }

    private val Attribute.isFlag get() = key == null && value.lowercase() in flags

    private val Char.isQuote get() = this == '"' || this == '\''

    private fun IntRange.shift(offset: Int) = (first + offset)..(last + offset)
}
