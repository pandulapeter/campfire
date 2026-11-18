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
 * ([ChordSheetHtml.chordIn]) or its chord cells hold ([ChordSheetHtmlCells]) at the index of the character they stand
 * before. Whitespace collapses as a browser collapses it (kept inside `<pre>`), what the inline style hides is left out,
 * and a fragment cut off mid-tag or with tags left open, as a copied selection can be, is read as far as it goes: a close
 * tag closes the nearest element of its name and everything opened inside it.
 *
 * An inline box (`display: inline-block` and its kin) is first read on its own, by a reader that stops where it closes,
 * since what it holds decides how it sits on its line. That reader gives up past the rows a cell has, so a box around a
 * whole page costs no more than its first lines, and boxes inside boxes are read so only [MAX_DEPTH] deep.
 */
internal class ChordSheetHtmlReader private constructor(
    private val html: String,
    private val box: String?,
    private val isSkipping: Boolean,
    private val depth: Int,
) {
    constructor(html: String) : this(html, box = null, isSkipping = false, depth = 0)

    class Line {
        val text = StringBuilder()
        val chords = mutableListOf<Pair<Int, String>>()
        val isEmpty get() = text.isBlank() && chords.isEmpty()
    }

    /** An inline box read on its own: its [lines], the first [brokenLines] of them ended by a block or a break. */
    class Box(val lines: List<Line>, val brokenLines: Int)

    /** @property chord The chord the element names, placed where it closes if nothing was put inside it. */
    private class Element(val name: String, val chord: String?)

    private val lines = mutableListOf<Line>()
    private var line = Line()
    private val open = mutableListOf<Element>()
    private val cells = ChordSheetHtmlCells()

    /**
     * How many of the [open] elements, counted from the outermost, had anything but whitespace put inside them: text, a
     * chord, or a line break. Content marks every element open at the time, and later ones open above them, so the marked
     * ones are always the outermost and a count says it in constant time, where a flag on each would make a fragment of
     * many unclosed tags take time growing with the square of its length.
     */
    private var withContent = 0

    /** How many elements of each name are [open], so that a stray close tag is passed over without a search. */
    private val openCounts = mutableMapOf<String, Int>()
    private var preformatted = 0
    private var isBoxClosed = false
    private var hasOverflowed = false

    fun read(): List<Line> {
        walk(0)
        return finish()
    }

    private fun finish(): List<Line> {
        cells.flushInto(line)
        lines += line
        return lines
    }

    /** Reads from [start] to the end, or to where the [box] closes, returning the index it stopped at. */
    private fun walk(start: Int): Int {
        var index = start
        while (index < html.length && !isBoxClosed && !hasOverflowed) {
            val next = html.indexOf('<', index).let { if (it < 0) html.length else it }
            if (next > index) appendText(ChordSheetHtmlEntities.decode(html.substring(index, next)))
            if (next >= html.length) return html.length
            index = when {
                html.startsWith("<!--", next) -> html.indexOf("-->", next + 4).let { if (it < 0) html.length else it + 3 }
                html.getOrNull(next + 1)?.let { it.isLetter() || it == '/' || it == '!' || it == '?' } == true -> readTag(next)
                else -> {
                    appendText("<")
                    next + 1
                }
            }
        }
        return index
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
            if (name == box && (openCounts[name] ?: 0) == 0) isBoxClosed = true else close(name)
            return after
        }
        if (name in RAW_TEXT) {
            val closing = html.indexOf("</$name", after, ignoreCase = true)
            return if (closing < 0) html.length else readTag(closing)
        }
        val isVoid = name in VOID || tag.endsWith("/")
        val attributes = tag.drop(name.length)
        if (!isSkipping && !isVoid) {
            val style = ChordSheetHtmlStyle.of(attributeValue(attributes, "style"))
            if (style.isHidden) return ChordSheetHtmlReader(html, name, isSkipping = true, depth = depth).walk(after)
            if (style.isInlineBox && depth < MAX_DEPTH) readBox(name, after)?.let { (box, end) ->
                if (cells.add(box, style)) {
                    if (cells.isPending) markContent()
                    return end
                }
            }
        }
        when {
            isSkipping -> Unit
            name == "br" -> breakLine(force = true)
            name in BLOCKS -> breakLine(force = false)
            name in CELLS -> appendText(" ")
        }
        if (name == "pre") preformatted++
        open += Element(name, if (isSkipping) null else chordOf(attributes))
        openCounts[name] = (openCounts[name] ?: 0) + 1
        if (isVoid) close(name)
        return after
    }

    /**
     * The element [name] opened right before [start] read on its own, with the index after it; null where it holds more
     * rows than a cell has, or a chord drawn from an attribute, which the outer walk places itself.
     */
    private fun readBox(name: String, start: Int): Pair<Box, Int>? {
        val reader = ChordSheetHtmlReader(html, name, isSkipping = false, depth = depth + 1)
        val end = reader.walk(start)
        if (reader.hasOverflowed) return null
        val brokenLines = reader.lines.size
        val lines = reader.finish()
        return if (lines.any { it.chords.isNotEmpty() }) null else Box(lines, brokenLines) to end
    }

    private fun close(name: String) {
        if ((openCounts[name] ?: 0) == 0) return
        val index = open.indexOfLast { it.name == name }
        while (open.size > index) {
            val hasContent = open.lastIndex < withContent
            val element = open.removeAt(open.lastIndex)
            withContent = minOf(withContent, open.size)
            openCounts[element.name] = openCounts.getValue(element.name) - 1
            if (element.name == "pre") preformatted--
            if (!hasContent && element.chord != null) {
                cells.flushInto(line)
                line.chords += line.text.length to element.chord
                markContent()
            }
            if (element.name in BLOCKS && !isSkipping) {
                // An empty block is a blank line, which is how a page spaces its verses with empty divs; any other only
                // ends the line it was on, and a paragraph leaves a blank line after it, as a browser's plain text does.
                breakLine(force = !hasContent && element.name !in VOID)
                if (element.name == "p" && lines.lastOrNull()?.isEmpty == false) breakLine(force = true)
            }
        }
    }

    private fun breakLine(force: Boolean) {
        cells.flushInto(line)
        if (!force && line.isEmpty) return
        lines += line
        line = Line()
        markContent()
        if (box != null && lines.count { !it.isEmpty } > ChordSheetHtmlCells.MAX_ROWS) hasOverflowed = true
    }

    private fun markContent() {
        withContent = open.size
    }

    private fun appendText(text: String) {
        if (isSkipping) return
        for (c in text) {
            when {
                preformatted > 0 -> {
                    cells.flushInto(line)
                    when (c) {
                        '\n' -> breakLine(force = true)
                        '\r' -> Unit
                        '\t' -> line.text.append(' ')
                        else -> line.text.append(c)
                    }
                }
                c.isHtmlSpace() -> if (cells.isPending) cells.space() else if (line.text.isNotEmpty() && line.text.last() != ' ') line.text.append(' ')
                else -> {
                    cells.flushInto(line)
                    line.text.append(c)
                }
            }
            if (!c.isWhitespace()) markContent()
        }
    }

    /** The first `data-*` attribute that holds a chord name, see [ChordSheetHtml.chordSheetText] for why the first. */
    private fun chordOf(attributes: String): String? = ATTRIBUTE.findAll(attributes).firstNotNullOfOrNull { match ->
        val value = match.valueOrNull()
        if (match.groupValues[1].startsWith("data-", ignoreCase = true) && value != null) {
            ChordSheetHtml.chordIn(ChordSheetHtmlEntities.decode(value))
        } else {
            null
        }
    }

    private fun attributeValue(attributes: String, name: String) = ATTRIBUTE.findAll(attributes)
        .firstOrNull { it.groupValues[1].equals(name, ignoreCase = true) }
        ?.valueOrNull()
        ?.let(ChordSheetHtmlEntities::decode)

    private fun MatchResult.valueOrNull() = groups[2]?.value ?: groups[3]?.value ?: groups[4]?.value

    /** The whitespace HTML collapses, which leaves out the no-break space. */
    private fun Char.isHtmlSpace() = this == ' ' || this == '\t' || this == '\n' || this == '\r' || code == FORM_FEED

    private companion object {
        const val FORM_FEED = 0x0C
        const val MAX_DEPTH = 8
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
