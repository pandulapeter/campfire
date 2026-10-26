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

    /** The attributes of a tag and the index right after the last of them. */
    class Read(val end: Int, val attributes: Map<String, String>)

    /**
     * The attributes starting at [start], or null where the text there is not attributes: each is a name, an `=`, and a
     * value, quoted or bare, after whitespace, and the text after them is a `>` or a `/`. A bare word (`<b c>`) is not
     * one, since that reads as lyrics. A bare value may hold slashes (`src=https://…`) but gives a last one back when a
     * `>` follows it, since that ends a self-closing tag (`<img src=a.png/>`). Names are lowercased; of a name written
     * twice the first wins.
     */
    fun read(text: String, start: Int): Read? {
        val attributes = mutableMapOf<String, String>()
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
            while (next < text.length && text[next].isWhitespace()) next++
            if (text.getOrNull(next) != '=') return null
            next++
            while (next < text.length && text[next].isWhitespace()) next++
            val quote = text.getOrNull(next)
            val value: String
            if (quote == '"' || quote == '\'') {
                val closingQuote = text.indexOf(quote, next + 1).takeIf { it >= 0 } ?: return null
                value = text.substring(next + 1, closingQuote)
                next = closingQuote + 1
            } else {
                val valueStart = next
                while (next < text.length && text[next].isBareValueCharacter()) next++
                if (next - valueStart > 1 && text[next - 1] == '/' && text.getOrNull(next) == '>') next--
                if (next == valueStart || text.substring(valueStart, next) == "/") return null
                value = text.substring(valueStart, next)
            }
            attributes.getOrPut(name) { value }
            index = next
        }
    }

    /**
     * The styles a `<span>` with [attributes] sets, of those a line can show: Pango's `weight`, `style`, and `underline`
     * (with their `font_` spellings, and the words of a `font` or `font_desc`), and the same in an HTML `style`, whose
     * value is CSS. A value that takes a style away (`weight="normal"`) sets nothing: the text keeps what the tags
     * around it give it.
     */
    fun spanStyles(attributes: Map<String, String>): Set<Formatting.Style> = buildSet {
        fun weight(value: String) {
            val trimmed = value.trim().lowercase()
            if (trimmed in boldWeights || (trimmed.toIntOrNull() ?: 0) >= 600) add(Formatting.Style.BOLD)
        }

        fun style(value: String) {
            if (value.trim().lowercase() in italicStyles) add(Formatting.Style.ITALIC)
        }
        (attributes["weight"] ?: attributes["font_weight"])?.let(::weight)
        attributes["font_style"]?.let(::style)
        attributes["underline"]?.let { if (it.trim().lowercase() in underlines) add(Formatting.Style.UNDERLINE) }
        (attributes["font_desc"] ?: attributes["font"])?.split(' ')?.forEach { word ->
            weight(word)
            style(word)
        }
        val styleValue = attributes["style"] ?: return@buildSet
        // Pango's `style` is a font style, HTML's is CSS; a value with no colon in it is Pango's.
        if (':' !in styleValue) style(styleValue) else styleValue.split(';').forEach { declaration ->
            val property = declaration.substringBefore(':').trim().lowercase()
            val value = declaration.substringAfter(':', "")
            when (property) {
                "font-weight" -> weight(value)
                "font-style" -> style(value)
                "text-decoration", "text-decoration-line" -> if ("underline" in value.lowercase()) add(Formatting.Style.UNDERLINE)
            }
        }
    }

    private val boldWeights = setOf("bold", "bolder", "semibold", "ultrabold", "heavy", "ultraheavy")
    private val italicStyles = setOf("italic", "oblique")
    private val underlines = setOf("single", "double", "low", "error")

    private fun Char.isNameCharacter() = isLetterOrDigit() || this == '-' || this == '_' || this == ':'

    private fun Char.isBareValueCharacter() = !isWhitespace() && this != '>' && this != '<' && this != '"' && this != '\''
}
