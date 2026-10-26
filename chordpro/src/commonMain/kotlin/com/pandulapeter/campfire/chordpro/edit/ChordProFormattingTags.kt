/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.edit

import com.pandulapeter.campfire.chordpro.model.ChordProLine.Lyrics.Formatting
import com.pandulapeter.campfire.chordpro.syntax.ChordProMarkup
import com.pandulapeter.campfire.chordpro.syntax.MarkupAttributes

/**
 * Where a selection is already set in a style by a pair of tags, so that an editor's Bold, Italic, or Underline takes the
 * style off rather than adding tags of its own: any spelling the parser reads ([ChordProMarkup]), a `<strong>` or a
 * `<span weight="bold">` as much as a `<b>`.
 *
 * Only the style is taken off, every other byte kept: a `<b>` pair goes, but a span loses only the attribute, the word of
 * its font, or the CSS declaration that sets the style, and goes itself only once nothing else is left in it, since its
 * other attributes (a colour, a size) are the author's and other ChordPro programs draw them.
 */
public object ChordProFormattingTags {

    /** Taking a style off: [text] in place of [start] until [end], with what the tags held at [contentStart] in it. */
    public class Removal internal constructor(
        public val start: Int,
        public val end: Int,
        public val text: String,
        public val contentStart: Int,
        public val contentLength: Int,
    )

    /**
     * The removal of [style] where an opening tag that sets it ends right at [start] of [text] and its closing tag starts
     * right at [end], both within [range] (the part of the line markup is read in), or null where no such pair does.
     */
    public fun around(text: String, start: Int, end: Int, style: Formatting.Style, range: IntRange): Removal? {
        val openingStart = openingStartEndingAt(text, start, range.first, style) ?: return null
        val opening = ChordProMarkup.tagAt(text, openingStart) ?: return null
        val closing = ChordProMarkup.tagAt(text, end)?.takeIf { it.isClosing && it.name == opening.name } ?: return null
        if (end + closing.length > range.last + 1) return null
        return removal(text, openingStart, opening, end, closing, style)
    }

    /**
     * The removal of [style] where the text from [start] to [end] of [text] starts with an opening tag that sets it and
     * ends with its closing tag, or null where it does not.
     */
    public fun inside(text: String, start: Int, end: Int, style: Formatting.Style): Removal? {
        val opening = ChordProMarkup.tagAt(text, start)?.takeIf { it.opens(style) } ?: return null
        val contentStart = start + opening.length
        var closingStart = text.lastIndexOf('<', end - 1)
        while (closingStart >= contentStart) {
            val closing = ChordProMarkup.tagAt(text, closingStart)
            if (closing != null && closingStart + closing.length == end) {
                return if (closing.isClosing && closing.name == opening.name) removal(text, start, opening, closingStart, closing, style) else null
            }
            closingStart = text.lastIndexOf('<', closingStart - 1)
        }
        return null
    }

    private fun ChordProMarkup.Tag.opens(style: Formatting.Style) = !isClosing && !isSelfClosing && style in styles

    private fun openingStartEndingAt(text: String, end: Int, from: Int, style: Formatting.Style): Int? {
        var index = text.lastIndexOf('<', end - 1)
        while (index >= from) {
            val tag = ChordProMarkup.tagAt(text, index)
            if (tag != null && index + tag.length == end) return index.takeIf { tag.opens(style) }
            index = text.lastIndexOf('<', index - 1)
        }
        return null
    }

    private fun removal(
        text: String,
        openingStart: Int,
        opening: ChordProMarkup.Tag,
        closingStart: Int,
        closing: ChordProMarkup.Tag,
        style: Formatting.Style,
    ): Removal {
        val contentStart = openingStart + opening.length
        val content = text.substring(contentStart, closingStart)
        val keptOpening = opening.attributes?.let { keptOpening(text, openingStart, contentStart, it, style) }
        val end = closingStart + closing.length
        return if (keptOpening == null) {
            Removal(start = openingStart, end = end, text = content, contentStart = openingStart, contentLength = content.length)
        } else {
            Removal(
                start = openingStart,
                end = end,
                text = keptOpening + content + text.substring(closingStart, end),
                contentStart = openingStart + keptOpening.length,
                contentLength = content.length,
            )
        }
    }

    /**
     * A span's opening tag from [start] to [end] without what sets [style], or null where nothing else would be left in
     * it (and for a tag that is the style itself, `<b>`, which has no part to cut). A part is cut alone where its
     * attribute keeps something else (`font_desc="Sans Bold"`, two CSS declarations), else the attribute is.
     */
    private fun keptOpening(text: String, start: Int, end: Int, read: MarkupAttributes.Read, style: Formatting.Style): String? {
        val parts = MarkupAttributes.styledParts(read.attributes).filter { it.style == style }
        if (parts.isEmpty()) return null
        val cuts = parts.groupBy { it.attribute }.flatMap { (attribute, attributeParts) ->
            val kept = StringBuilder(attribute.value)
            attributeParts.map { it.range }.filter { it != attribute.range }.sortedByDescending { it.first }.forEach { range ->
                kept.deleteRange(range.first - attribute.valueStart, range.last + 1 - attribute.valueStart)
            }
            val isEmptied = attributeParts.any { it.range == attribute.range } || kept.all { it.isWhitespace() || it == ';' }
            if (isEmptied) listOf(attribute.range) else attributeParts.map { it.range }
        }
        if (read.attributes.all { it.range in cuts }) return null
        val opening = StringBuilder(text.substring(start, end))
        cuts.sortedByDescending { it.first }.forEach { range -> opening.deleteRange(range.first - start, range.last + 1 - start) }
        return opening.toString()
    }
}
