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

import com.pandulapeter.campfire.chordpro.model.ChordProLine.Lyrics.Formatting

/** The attributes of a markup tag, and the styles a `<span>` sets with them, for [ChordProMarkup]. */
internal object MarkupAttributes {

    /**
     * An attribute as written: [range] runs from the whitespace before its name to the end of its value, which is what
     * cutting it out of its tag takes away, and [valueStart] is where its value, without quotes, starts in the text.
     */
    class Attribute(val name: String, val value: String, val range: IntRange, val valueStart: Int)

    /** The attributes of a tag and the index right after the last of them. */
    class Read(val end: Int, val attributes: List<Attribute>) {

        /** The value of the first attribute named [name], lowercase. */
        fun valueOf(name: String) = attributes.firstOrNull { it.name == name }?.value
    }

    /** A part of a span's attributes that sets [style]: a whole attribute, a word of a font, or a CSS declaration. */
    class StyledPart(val style: Formatting.Style, val attribute: Attribute, val range: IntRange)

    /**
     * The attributes starting at [start], or null where the text there is not attributes: each is a name, an `=`, and a
     * value, quoted or bare, after whitespace, and the text after them is a `>` or a `/`. A bare word (`<b c>`) is not
     * one, since that reads as lyrics, unless [allowsBareNames] (a `<sym>` names its symbol so), when it is read with an
     * empty value. A bare value may hold slashes (`src=https://…`) but gives a last one back when a `>` follows it,
     * since that ends a self-closing tag (`<img src=a.png/>`). Names are lowercased.
     */
    fun read(text: String, start: Int, allowsBareNames: Boolean): Read? {
        val attributes = mutableListOf<Attribute>()
        var index = start
        while (true) {
            var next = index
            while (next < text.length && text[next].isWhitespace()) next++
            if (next == index || text.getOrNull(next)?.isLetter() != true) {
                return if (text.getOrNull(next) == '>' || text.getOrNull(next) == '/') Read(end = next, attributes = attributes) else null
            }
            val nameStart = next
            while (next < text.length && text[next].isNameCharacter()) next++
            val name = text.substring(nameStart, next).lowercase()
            val nameEnd = next
            while (next < text.length && text[next].isWhitespace()) next++
            if (text.getOrNull(next) != '=') {
                if (!allowsBareNames) return null
                attributes += Attribute(name = name, value = "", range = index until nameEnd, valueStart = nameEnd)
                index = nameEnd
                continue
            }
            next++
            while (next < text.length && text[next].isWhitespace()) next++
            val quote = text.getOrNull(next)
            val valueStart: Int
            val value: String
            if (quote == '"' || quote == '\'') {
                val closingQuote = text.indexOf(quote, next + 1).takeIf { it >= 0 } ?: return null
                valueStart = next + 1
                value = text.substring(valueStart, closingQuote)
                next = closingQuote + 1
            } else {
                valueStart = next
                while (next < text.length && text[next].isBareValueCharacter()) next++
                if (next - valueStart > 1 && text[next - 1] == '/' && text.getOrNull(next) == '>') next--
                if (next == valueStart || text.substring(valueStart, next) == "/") return null
                value = text.substring(valueStart, next)
            }
            attributes += Attribute(name = name, value = value, range = index until next, valueStart = valueStart)
            index = next
        }
    }

    /** The styles a `<span>` with [attributes] sets, see [styledParts]. */
    fun spanStyles(attributes: List<Attribute>): Set<Formatting.Style> = styledParts(attributes).mapTo(mutableSetOf()) { it.style }

    /**
     * The parts of a `<span>`'s [attributes] that set a style a line can show: Pango's `weight`, `style`, and `underline`
     * (with their `font_` spellings, and the words of a `font` or `font_desc`), and the same in an HTML `style`, whose
     * value is CSS. A value that takes a style away (`weight="normal"`) sets nothing: the text keeps what the tags
     * around it give it. Each part's range is what taking its style off cuts out of the text.
     */
    fun styledParts(attributes: List<Attribute>): List<StyledPart> = buildList {
        attributes.forEach { attribute ->
            val whole = attribute.range
            when (attribute.name) {
                "weight", "font_weight" -> weightOf(attribute.value)?.let { add(StyledPart(it, attribute, whole)) }
                "font_style" -> fontStyleOf(attribute.value)?.let { add(StyledPart(it, attribute, whole)) }
                "underline" -> if (attribute.value.trim().lowercase() in underlines) add(StyledPart(Formatting.Style.UNDERLINE, attribute, whole))
                "font", "font_desc" -> attribute.wordRanges().forEach { (word, range) ->
                    (weightOf(word) ?: fontStyleOf(word))?.let { add(StyledPart(it, attribute, range)) }
                }
                // Pango's `style` is a font style, HTML's is CSS; a value with no colon in it is Pango's.
                "style" -> if (':' !in attribute.value) {
                    fontStyleOf(attribute.value)?.let { add(StyledPart(it, attribute, whole)) }
                } else {
                    attribute.declarationRanges().forEach { (declaration, range) ->
                        val value = declaration.substringAfter(':', "")
                        val style = when (declaration.substringBefore(':').trim().lowercase()) {
                            "font-weight" -> weightOf(value)
                            "font-style" -> fontStyleOf(value)
                            "text-decoration", "text-decoration-line" -> Formatting.Style.UNDERLINE.takeIf { "underline" in value.lowercase() }
                            else -> null
                        }
                        style?.let { add(StyledPart(it, attribute, range)) }
                    }
                }
            }
        }
    }

    private fun weightOf(value: String) = value.trim().lowercase().let { trimmed ->
        Formatting.Style.BOLD.takeIf { trimmed in boldWeights || (trimmed.toIntOrNull() ?: 0) >= 600 }
    }

    private fun fontStyleOf(value: String) = Formatting.Style.ITALIC.takeIf { value.trim().lowercase() in italicStyles }

    /**
     * The words of a font description, each with its range in the text: the spaces before it, or for the first word the
     * ones after it, so that cutting one leaves the others as they were spaced.
     */
    private fun Attribute.wordRanges() = buildList {
        var index = 0
        while (index < value.length) {
            val spaceStart = index
            while (index < value.length && value[index] == ' ') index++
            val wordStart = index
            while (index < value.length && value[index] != ' ') index++
            if (index == wordStart) continue
            var rangeEnd = index
            if (spaceStart == 0) while (rangeEnd < value.length && value[rangeEnd] == ' ') rangeEnd++
            add(value.substring(wordStart, index) to (valueStart + spaceStart until valueStart + rangeEnd))
        }
    }

    /** The declarations of a CSS value, each with its range in the text, its `;` and the whitespace after it included. */
    private fun Attribute.declarationRanges() = buildList {
        var index = 0
        while (index < value.length) {
            var end = value.indexOf(';', index).let { if (it < 0) value.length else it + 1 }
            val declaration = value.substring(index, end).removeSuffix(";")
            while (end < value.length && value[end].isWhitespace()) end++
            add(declaration to (valueStart + index until valueStart + end))
            index = end
        }
    }

    private val boldWeights = setOf("bold", "bolder", "semibold", "ultrabold", "heavy", "ultraheavy")
    private val italicStyles = setOf("italic", "oblique")
    private val underlines = setOf("single", "double", "low", "error")

    private fun Char.isNameCharacter() = isLetterOrDigit() || this == '-' || this == '_' || this == ':'

    private fun Char.isBareValueCharacter() = !isWhitespace() && this != '>' && this != '<' && this != '"' && this != '\''
}
