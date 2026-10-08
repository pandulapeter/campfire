/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.formats.document

import com.pandulapeter.campfire.chordpro.convert.ChordSheetConverter
import com.pandulapeter.campfire.data.model.domain.ExtractedDocument
import com.pandulapeter.campfire.data.model.domain.ImportLimits
import com.pandulapeter.campfire.data.formats.zip.ZipReader
import kotlinx.coroutines.yield

object DocxTextExtractor {
    suspend fun extract(bytes: ByteArray): ExtractedDocument {
        require(bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4b.toByte())
        val parts = ZipReader.read(bytes, maxTotalSize = ImportLimits.MAX_DOCUMENT_FILE_SIZE, limitOf = { name ->
            ImportLimits.MAX_TEXT_FILE_SIZE.takeIf { name == "word/document.xml" || name == "word/styles.xml" }
        }).entries.associate { it.name to it.bytes.decodeToString(throwOnInvalidSequence = true) }
        return fromXml(requireNotNull(parts["word/document.xml"]), parts["word/styles.xml"])
    }

    internal suspend fun fromXml(document: String, stylesXml: String? = null): ExtractedDocument {
        val styles = stylesXml?.let(::parseXml)
        val definitions = styles?.children?.filter { it.localName == "style" }?.associateBy { it.attribute("styleId").orEmpty() }.orEmpty()
        val defaultParagraph = definitions.entries.firstOrNull { it.value.attribute("type") == "paragraph" && it.value.attribute("default") in listOf("1", "true") }?.key
        val defaults = Style().with(styles?.child("docDefaults")?.child("rPrDefault")?.child("rPr"))
        fun style(id: String?, seen: Set<String> = emptySet()): Style {
            if (id == null || id in seen || seen.size >= 32) return defaults
            val definition = definitions[id] ?: return defaults
            return style(definition.child("basedOn")?.attribute("val"), seen + id).with(definition.child("rPr"))
        }
        val root = parseXml(document)
        require(root.localName == "document")
        val body = requireNotNull(root.child("body"))
        val pages = mutableListOf<MutableList<ExtractedDocument.Line>>(mutableListOf())
        var characters = 0L
        fun page() { require(pages.size < 2000); pages += mutableListOf<ExtractedDocument.Line>() }
        fun append(line: ExtractedDocument.Line) {
            characters += line.spans.sumOf { it.text.length }.toLong()
            require(characters <= ImportLimits.MAX_TEXT_FILE_SIZE / 3)
            require(pages.last().size < 100_000)
            pages.last() += line
        }

        fun paragraph(p: XmlElement, emit: (ExtractedDocument.Line) -> Unit, breakPage: () -> Unit) {
            val properties = p.child("pPr")
            val styleId = properties?.child("pStyle")?.attribute("val") ?: defaultParagraph
            val definition = definitions[styleId]
            val name = definition?.child("name")?.attribute("val") ?: styleId.orEmpty()
            val heading = name.contains("title", true) || name.contains("heading", true)
            val base = style(styleId).with(properties?.child("rPr"))
            val tabStops = (properties?.child("tabs") ?: definition?.child("pPr")?.child("tabs"))?.children
                ?.filter { it.attribute("val") != "clear" }?.mapNotNull { it.attribute("pos")?.toDoubleOrNull()?.div(20)?.takeIf { position -> position.isFinite() && position in 0.0..1_000_000.0 } }?.sorted().orEmpty()
            var x = 0.0
            var spans = mutableListOf<ExtractedDocument.Span>()
            fun line() { emit(ExtractedDocument.Line(spans.toList(), heading)); spans = mutableListOf(); x = 0.0 }
            fun text(value: String, runStyle: Style) {
                if (value.isEmpty()) return
                val end = x + value.sumOf { width(it, runStyle) }
                spans += ExtractedDocument.Span(value, x, end, runStyle.size, runStyle.bold, runStyle.monospace, runStyle.raised)
                x = end
            }
            fun walk(element: XmlElement, inherited: Style) {
                // Word writes a text box twice, for itself and as an older shape in the fallback, and a tracked move
                // both where the text was and where it went; reading both would import the same lines twice.
                if (element.localName in listOf("del", "moveFrom", "Fallback", "pPr", "rPr")) return
                val current = if (element.localName == "r") {
                    val rPr = element.child("rPr")
                    val runId = rPr?.child("rStyle")?.attribute("val")
                    (if (runId != null) inherited.with(definitions[runId]?.child("rPr")) else inherited).with(rPr)
                } else inherited
                when (element.localName) {
                    "t" -> text(element.text, current)
                    "tab" -> x = tabStops.firstOrNull { it > x + 0.01 } ?: ((x / 36).toInt() + 1) * 36.0
                    "br", "cr" -> { line(); if (element.attribute("type") == "page") breakPage() }
                    "lastRenderedPageBreak" -> { if (spans.isNotEmpty()) line(); breakPage() }
                    "noBreakHyphen" -> text("-", current)
                    "softHyphen" -> Unit
                    "p" -> { if (spans.isNotEmpty()) line(); paragraph(element, emit, breakPage) }
                    else -> element.children.forEach { walk(it, current) }
                }
            }
            properties?.child("pageBreakBefore")?.let { if (it.attribute("val") !in listOf("0", "false", "off")) breakPage() }
            p.children.forEach { walk(it, base) }
            line()
            if (properties?.child("sectPr")?.child("type")?.attribute("val") in listOf("nextPage", "oddPage", "evenPage")) breakPage()
        }

        suspend fun table(table: XmlElement) {
            val cells = table.unwrapped().filter { it.localName == "tr" }.map { row -> row.unwrapped().filter { it.localName == "tc" } }
            val rows = cells.map { row ->
                row.map { cell ->
                    val lines = mutableListOf<ExtractedDocument.Line>()
                    cell.unwrapped().filter { it.localName == "p" }.forEach { paragraph(it, lines::add, {}) }
                    lines
                }
            }
            fun cellText(lines: List<ExtractedDocument.Line>) = lines.joinToString(" ") { it.spans.joinToString("") { span -> span.text } }.trim()
            val grid = rows.size >= 2 && rows.size % 2 == 0 && rows.chunked(2).all { pair ->
                pair[0].size == pair[1].size && pair[0].isNotEmpty() && pair[0].all { ChordSheetConverter.isChordLine(cellText(it)) } &&
                    pair[1].any { cellText(it).isNotBlank() && !ChordSheetConverter.isChordLine(cellText(it)) }
            }
            if (grid) for (pair in rows.chunked(2)) {
                val chords = mutableListOf<ExtractedDocument.Span>()
                val lyrics = mutableListOf<ExtractedDocument.Span>()
                var x = 0.0
                pair[0].indices.forEach { index ->
                    fun shift(lines: List<ExtractedDocument.Line>): List<ExtractedDocument.Span> {
                        var end = x
                        return lines.flatMap { line ->
                            val start = end
                            line.spans.map { span -> span.copy(start = start + span.start, end = start + span.end) }.also {
                                end = (it.lastOrNull()?.end ?: start) + 6
                            }
                        }
                    }
                    val c = shift(pair[0][index])
                    val l = shift(pair[1][index])
                    chords += c
                    lyrics += l
                    x = maxOf(c.lastOrNull()?.end ?: x, l.lastOrNull()?.end ?: x) + 12
                }
                append(ExtractedDocument.Line(chords)); append(ExtractedDocument.Line(lyrics))
                yield()
            } else {
                for (row in cells) for (cell in row) {
                    for (element in cell.unwrapped()) when (element.localName) {
                        "p" -> paragraph(element, ::append, ::page)
                        "tbl" -> table(element)
                    }
                    yield()
                }
            }
        }
        for (element in body.unwrapped()) {
            when (element.localName) {
                "p" -> paragraph(element, ::append, ::page)
                "tbl" -> table(element)
            }
            yield()
        }
        return ExtractedDocument(pages.filter { it.isNotEmpty() }.map { ExtractedDocument.Page(it.toList()) })
    }

    /**
     * The children with every content control replaced by what it holds: Word wraps paragraphs, tables, table rows and
     * cells in them (cover pages, tables of contents, repeating sections). Controls nested deeper than
     * [MAX_CONTENT_CONTROL_DEPTH] are left out, which keeps the recursion bounded whatever the XML's own depth limit.
     */
    private fun XmlElement.unwrapped(depth: Int = 0): List<XmlElement> = children.flatMap { child ->
        when {
            child.localName != "sdt" -> listOf(child)
            depth >= MAX_CONTENT_CONTROL_DEPTH -> emptyList()
            else -> child.child("sdtContent")?.unwrapped(depth + 1).orEmpty()
        }
    }

    private const val MAX_CONTENT_CONTROL_DEPTH = 16

    private data class Style(val size: Double = 12.0, val bold: Boolean = false, val font: String = "", val raised: Boolean = false) {
        val monospace get() = listOf("courier", "consolas", "monaco", "menlo", "liberation mono", "dejavu sans mono", "lucida console").any { font.contains(it, true) }
        fun with(properties: XmlElement?): Style {
            if (properties == null) return this
            val b = properties.child("b")
            return copy(
                size = (properties.child("sz")?.attribute("val")?.toDoubleOrNull()?.div(2)?.takeIf { it.isFinite() } ?: size).coerceIn(1.0, 200.0),
                bold = if (b == null) bold else b.attribute("val") !in listOf("0", "false", "off"),
                font = properties.child("rFonts")?.let { it.attribute("ascii") ?: it.attribute("hAnsi") } ?: font,
                raised = properties.child("vertAlign")?.attribute("val")?.let { it == "superscript" } ?: raised,
            )
        }
    }
    private fun width(c: Char, style: Style): Double = style.size * when {
        style.monospace -> 0.6
        c == ' ' -> 0.28
        c in "iljtf.,'!:;|" -> 0.28
        c in "mwMW@" -> 0.85
        c.isUpperCase() -> 0.65
        c.isDigit() -> 0.55
        else -> 0.5
    }
}
