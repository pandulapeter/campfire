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

import com.pandulapeter.campfire.chordpro.model.ChordProLine
import com.pandulapeter.campfire.chordpro.model.ChordProLine.Lyrics.Formatting

/**
 * The markup of a lyrics line or a comment: the subset of ChordPro's Pango markup that sets text in bold (`<b>`, `<strong>`), italic
 * (`<i>`, `<em>`), or underlined (`<u>`), in any letter case. The other tags of Pango and of HTML's inline text (`<span>`,
 * `<sup>`, `<font>`, `<br>`…) are markup too, read and dropped: the file keeps them, the page shows the text they wrap.
 * A tag may carry attributes written `name=value` and close itself (`<br/>`). Anything else in angle brackets is lyrics,
 * so a `<3`, an `<intro>`, or an `a<b c>` written into a line stays as it is: a tag named only by the song's author is
 * far likelier to be a word they meant to show.
 *
 * Markup belongs to one line: a tag left open is closed at the end of it, and a closing tag with nothing open is
 * dropped, since the file is the user's and a stray tag must neither reach the page nor swallow the rest of the song.
 *
 * The parser strips it from lyrics lines into [ChordProLine.Lyrics.formatting]. A comment keeps it in its text, since
 * the chords written into a comment are rewritten in that text after parsing: whoever draws one calls [strip] on it.
 */
public object ChordProMarkup {

    private val tags = mapOf(
        "b" to Formatting.Style.BOLD,
        "strong" to Formatting.Style.BOLD,
        "i" to Formatting.Style.ITALIC,
        "em" to Formatting.Style.ITALIC,
        "u" to Formatting.Style.UNDERLINE,
    )
    private val ignoredTags = setOf(
        "a", "abbr", "big", "br", "cite", "code", "del", "div", "font", "ins", "kbd", "mark", "p", "q", "s", "samp",
        "small", "span", "strike", "sub", "sup", "tt", "var", "wbr",
    )
    private val longestTagName = (tags.keys + ignoredTags).maxOf { it.length }

    /** A text with its markup taken out, see [strip]. */
    public class Stripped internal constructor(
        public val text: String,
        public val formatting: List<Formatting>,
        private val offsets: IntArray?,
    ) {

        /**
         * Where the character at [original] in the text before [strip] stands in [text]; a tag maps to the character
         * after it, so a chord written before a tag stays with the word the tag formats.
         */
        internal fun offsetOf(original: Int) = offsets?.let { it[original.coerceIn(0, it.lastIndex)] } ?: original
    }

    /** [text] without its markup tags, with the ranges they formatted, in the order of [ChordProLine.Lyrics.formatting]. */
    public fun strip(text: String): Stripped {
        if ('<' !in text) return Stripped(text = text, formatting = emptyList(), offsets = null)
        val result = StringBuilder(text.length)
        val offsets = IntArray(text.length + 1)
        val depths = IntArray(Formatting.Style.entries.size)
        val starts = IntArray(Formatting.Style.entries.size)
        val formatting = mutableListOf<Formatting>()
        var index = 0
        while (index < text.length) {
            val tag = if (text[index] == '<') tagAt(text, index) else null
            if (tag == null) {
                offsets[index] = result.length
                result.append(text[index])
                index++
                continue
            }
            for (tagIndex in index until index + tag.length) offsets[tagIndex] = result.length
            index += tag.length
            val style = tag.style ?: continue
            val styleIndex = style.ordinal
            if (tag.isClosing) {
                // Nested tags of one style (`<b><strong>`) are one range, ending with the outermost.
                if (depths[styleIndex] > 0 && --depths[styleIndex] == 0) formatting.addRange(starts[styleIndex], result.length, style)
            } else if (!tag.isSelfClosing && depths[styleIndex]++ == 0) {
                starts[styleIndex] = result.length
            }
        }
        offsets[text.length] = result.length
        Formatting.Style.entries.forEach { style ->
            if (depths[style.ordinal] > 0) formatting.addRange(starts[style.ordinal], result.length, style)
        }
        return Stripped(text = result.toString(), formatting = formatting.sortedWith(compareBy({ it.start }, { it.style })), offsets = offsets)
    }

    /** The tag that writes [style], without its angle brackets: `b`, `i`, or `u`. */
    public fun tagOf(style: Formatting.Style): String = when (style) {
        Formatting.Style.BOLD -> "b"
        Formatting.Style.ITALIC -> "i"
        Formatting.Style.UNDERLINE -> "u"
    }

    /** Adds the range, joined onto one of the same style it continues (`<b>a</b><b>b</b>`), unless it is empty. */
    private fun MutableList<Formatting>.addRange(start: Int, end: Int, style: Formatting.Style) {
        if (end <= start) return
        val previous = indexOfLast { it.style == style }
        if (previous >= 0 && this[previous].end == start) this[previous] = this[previous].copy(end = end) else add(Formatting(start, end, style))
    }

    /** A tag read at a `<`; [style] is null for one that is read only to be dropped. */
    private class Tag(val style: Formatting.Style?, val isClosing: Boolean, val isSelfClosing: Boolean, val length: Int)

    private fun tagAt(text: String, start: Int): Tag? {
        var index = start + 1
        val isClosing = text.getOrNull(index) == '/'
        if (isClosing) index++
        val nameStart = index
        if (text.getOrNull(index)?.isLetter() != true) return null
        while (index < text.length && index - nameStart <= longestTagName && text[index].isLetterOrDigit()) index++
        val name = text.substring(nameStart, index).lowercase()
        val style = tags[name]
        if (style == null && name !in ignoredTags) return null
        if (!isClosing) index = attributesEnd(text, index) ?: return null
        val isSelfClosing = !isClosing && text.getOrNull(index) == '/'
        if (isSelfClosing) index++
        if (text.getOrNull(index) != '>') return null
        return Tag(style = style, isClosing = isClosing, isSelfClosing = isSelfClosing, length = index + 1 - start)
    }

    /**
     * Where the attributes starting at [start] end, or null where the text there is not attributes: each is a name, an
     * `=`, and a value, quoted or bare, after whitespace. A bare word (`<b c>`) is not one, since that reads as lyrics, and
     * a bare value has no slash, which `<br/>` would otherwise lose to it.
     */
    private fun attributesEnd(text: String, start: Int): Int? {
        var index = start
        while (true) {
            var next = index
            while (next < text.length && text[next].isWhitespace()) next++
            if (next == index || text.getOrNull(next)?.isLetter() != true) return next.takeIf { text.getOrNull(it) == '>' || text.getOrNull(it) == '/' }
            while (next < text.length && (text[next].isLetterOrDigit() || text[next] == '-' || text[next] == '_' || text[next] == ':')) next++
            while (next < text.length && text[next].isWhitespace()) next++
            if (text.getOrNull(next) != '=') return null
            next++
            while (next < text.length && text[next].isWhitespace()) next++
            val quote = text.getOrNull(next)
            if (quote == '"' || quote == '\'') {
                next = text.indexOf(quote, next + 1).takeIf { it >= 0 }?.plus(1) ?: return null
            } else {
                val valueStart = next
                while (next < text.length && text[next].isBareValueCharacter()) next++
                if (next == valueStart) return null
            }
            index = next
        }
    }

    private fun Char.isBareValueCharacter() = !isWhitespace() && this != '>' && this != '<' && this != '/' && this != '"' && this != '\''
}
