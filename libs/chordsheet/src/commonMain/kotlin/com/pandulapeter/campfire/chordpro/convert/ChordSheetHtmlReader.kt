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

/**
 * One walk over an HTML fragment into the lines a browser lays it out in, each with the chords its empty elements name
 * ([ChordSheetHtml.chordIn]) at the index of the character they stand before. Whitespace collapses as a browser collapses
 * it (kept inside `<pre>`), and a fragment cut off mid-tag or with tags left open, as a copied selection can be, is read
 * as far as it goes: a close tag closes the nearest element of its name and everything opened inside it.
 */
internal class ChordSheetHtmlReader(private val html: String) {

    class Line {
        val text = StringBuilder()
        val chords = mutableListOf<Pair<Int, String>>()
        val isEmpty get() = text.isBlank() && chords.isEmpty()
    }

    /**
     * @property chord The chord the element names, placed where it closes if nothing was put inside it.
     * @property hasContent Whether anything but whitespace was put inside it: text, a chord, or a line break.
     */
    private class Element(val name: String, val chord: String?, var hasContent: Boolean = false)

    private val lines = mutableListOf<Line>()
    private var line = Line()
    private val open = mutableListOf<Element>()
    private var preformatted = 0

    fun read(): List<Line> {
        var index = 0
        while (index < html.length) {
            val next = html.indexOf('<', index).let { if (it < 0) html.length else it }
            if (next > index) appendText(ChordSheetHtmlEntities.decode(html.substring(index, next)))
            if (next >= html.length) break
            index = when {
                html.startsWith("<!--", next) -> html.indexOf("-->", next + 4).let { if (it < 0) html.length else it + 3 }
                html.getOrNull(next + 1)?.let { it.isLetter() || it == '/' || it == '!' || it == '?' } == true -> readTag(next)
                else -> {
                    appendText("<")
                    next + 1
                }
            }
        }
        lines += line
        return lines
    }

    /** Reads the tag starting at [start], returning the index after it. */
    private fun readTag(start: Int): Int {
        var quote: Char? = null
        var end = start + 1
        while (end < html.length) {
            val c = html[end]
            when {
                quote != null -> if (c == quote) quote = null
                c == '"' || c == '\'' -> quote = c
                c == '>' -> break
            }
            end++
        }
        val tag = html.substring(start + 1, end)
        val after = (end + 1).coerceAtMost(html.length)
        if (tag.startsWith("!") || tag.startsWith("?")) return after
        val isClosing = tag.startsWith("/")
        val name = tag.removePrefix("/").takeWhile { !it.isHtmlSpace() && it != '/' }.lowercase()
        if (isClosing) {
            close(name)
            return after
        }
        if (name in RAW_TEXT) {
            val closing = html.indexOf("</$name", after, ignoreCase = true)
            return if (closing < 0) html.length else readTag(closing)
        }
        when {
            name == "br" -> breakLine(force = true)
            name in BLOCKS -> breakLine(force = false)
            name in CELLS -> appendText(" ")
        }
        if (name == "pre") preformatted++
        open += Element(name, chordOf(tag.drop(name.length)))
        if (name in VOID || tag.endsWith("/")) close(name)
        return after
    }

    private fun close(name: String) {
        val index = open.indexOfLast { it.name == name }
        if (index < 0) return
        while (open.size > index) {
            val element = open.removeAt(open.lastIndex)
            if (element.name == "pre") preformatted--
            if (!element.hasContent && element.chord != null) {
                line.chords += line.text.length to element.chord
                markContent()
            }
            if (element.name in BLOCKS) {
                // An empty block is a blank line, which is how a page spaces its verses with empty divs; any other only
                // ends the line it was on, and a paragraph leaves a blank line after it, as a browser's plain text does.
                breakLine(force = !element.hasContent && element.name !in VOID)
                if (element.name == "p" && lines.lastOrNull()?.isEmpty == false) breakLine(force = true)
            }
        }
    }

    private fun breakLine(force: Boolean) {
        if (!force && line.isEmpty) return
        lines += line
        line = Line()
        markContent()
    }

    private fun markContent() = open.forEach { it.hasContent = true }

    private fun appendText(text: String) {
        for (c in text) {
            when {
                preformatted > 0 -> when (c) {
                    '\n' -> breakLine(force = true)
                    '\r' -> Unit
                    '\t' -> line.text.append(' ')
                    else -> line.text.append(c)
                }
                c.isHtmlSpace() -> if (line.text.isNotEmpty() && line.text.last() != ' ') line.text.append(' ')
                else -> line.text.append(c)
            }
            if (!c.isWhitespace()) markContent()
        }
    }

    /** The first `data-*` attribute that holds a chord name, see [ChordSheetHtml.chordSheetText] for why the first. */
    private fun chordOf(attributes: String): String? = ATTRIBUTE.findAll(attributes).firstNotNullOfOrNull { match ->
        val value = match.groups[2]?.value ?: match.groups[3]?.value ?: match.groups[4]?.value
        if (match.groupValues[1].startsWith("data-", ignoreCase = true) && value != null) {
            ChordSheetHtml.chordIn(ChordSheetHtmlEntities.decode(value))
        } else {
            null
        }
    }

    /** The whitespace HTML collapses, which leaves out the no-break space. */
    private fun Char.isHtmlSpace() = this == ' ' || this == '\t' || this == '\n' || this == '\r' || code == FORM_FEED

    private companion object {
        const val FORM_FEED = 0x0C
        val ATTRIBUTE = Regex("""([^\s"'>/=]+)(?:\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'=<>`]+)))?""")
        val RAW_TEXT = setOf("script", "style", "title", "template", "noscript", "textarea")
        val VOID = setOf("area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "source", "track", "wbr")
        val CELLS = setOf("td", "th")
        val BLOCKS = setOf(
            "address", "article", "aside", "blockquote", "br", "caption", "dd", "details", "dialog", "div", "dl", "dt",
            "fieldset", "figcaption", "figure", "footer", "form", "h1", "h2", "h3", "h4", "h5", "h6", "header", "hr", "li",
            "main", "nav", "ol", "p", "pre", "section", "summary", "table", "tr", "ul",
        )
    }
}
