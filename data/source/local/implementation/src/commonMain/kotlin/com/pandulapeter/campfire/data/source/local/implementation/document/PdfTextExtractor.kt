/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.document

import com.pandulapeter.campfire.data.model.domain.ExtractedDocument
import com.pandulapeter.campfire.data.model.domain.ImportLimits
import kotlinx.coroutines.yield
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Text-only PDF interpreter. Images, paths and embedded font programs are never opened. */
internal object PdfTextExtractor {
    /**
     * Every glyph that is kept becomes a span of its own until the lines are built, so this is what bounds the memory
     * a document can take, and it is set well above a songbook of several hundred songs: anything beyond it is
     * reported as unreadable, which a real songbook must not be.
     */
    internal const val MAX_GLYPHS = 1_000_000
    /** The glyphs shown at all, readable or not, which bounds the time a document can take. */
    internal const val MAX_SHOWN_GLYPHS = 2 * MAX_GLYPHS
    private data class Point(val x: Double, val y: Double)
    private data class Matrix(val a: Double = 1.0, val b: Double = 0.0, val c: Double = 0.0, val d: Double = 1.0, val e: Double = 0.0, val f: Double = 0.0) {
        fun point(x: Double, y: Double) = Point(a * x + c * y + e, b * x + d * y + f)
        operator fun times(other: Matrix) = Matrix(
            a * other.a + c * other.b, b * other.a + d * other.b,
            a * other.c + c * other.d, b * other.c + d * other.d,
            a * other.e + c * other.f + e, b * other.e + d * other.f + f,
        )
        fun translated(x: Double, y: Double) = this * Matrix(e = x, f = y)
    }
    private data class State(
        var ctm: Matrix = Matrix(), var text: Matrix = Matrix(), var line: Matrix = Matrix(),
        var font: PdfFont? = null, var size: Double = 12.0, var charSpace: Double = 0.0,
        var wordSpace: Double = 0.0, var scale: Double = 1.0, var leading: Double = 0.0, var rise: Double = 0.0,
        var inText: Boolean = false,
    )
    private data class Positioned(val span: ExtractedDocument.Span, val y: Double)
    private data class Line(val spans: List<ExtractedDocument.Span>, val y: Double) {
        val text get() = spans.joinToString("") { it.text }
    }
    private data class Page(val lines: List<Line>, val height: Double)

    suspend fun extract(bytes: ByteArray): ExtractedDocument {
        val file = PdfFile(bytes)
        val pages = mutableListOf<Page>()
        val fonts = mutableMapOf<PdfDictionary, PdfFont>()
        val visited = mutableSetOf<PdfValue>()
        var shown = 0L
        var unreadable = 0L
        var textBytes = 0L
        var operators = 0
        var glyphCount = 0

        suspend fun page(dictionary: PdfDictionary, resources: PdfDictionary?, box: List<PdfValue>, rotation: Int) {
            require(pages.size < 2000)
            val bounds = box.map { file.number(it) }
            require(bounds.size == 4 && bounds.all { it.isFinite() && abs(it) < 1_000_000 })
            val rotate = ((rotation % 360) + 360) % 360
            // A rectangle may name any two opposite corners, in either order.
            val x0 = minOf(bounds[0], bounds[2]); val y0 = minOf(bounds[1], bounds[3])
            val x1 = maxOf(bounds[0], bounds[2]); val y1 = maxOf(bounds[1], bounds[3])
            require(x1 > x0 && y1 > y0 && rotate % 90 == 0)
            fun point(point: Point) = when (rotate) {
                90 -> Point(point.y - y0, point.x - x0)
                180 -> Point(x1 - point.x, point.y - y0)
                270 -> Point(y1 - point.y, x1 - point.x)
                else -> Point(point.x - x0, y1 - point.y)
            }
            val glyphs = mutableListOf<Positioned>()
            val activeForms = mutableSetOf<PdfStream>()
            suspend fun interpret(data: ByteArray, resource: PdfDictionary?, initial: State, depth: Int = 0) {
                require(depth <= 32)
                val parser = PdfSyntax(data)
                var state = initial.copy()
                val saved = mutableListOf<State>()
                val operands = mutableListOf<PdfValue>()
                fun n(index: Int, fallback: Double = 0.0) = operands.getOrNull(index).number(fallback)
                fun matrix(values: List<PdfValue>): Matrix {
                    require(values.all { it.number().isFinite() && abs(it.number()) <= 1_000_000 })
                    return Matrix(values.getOrNull(0).number(1.0), values.getOrNull(1).number(), values.getOrNull(2).number(), values.getOrNull(3).number(1.0), values.getOrNull(4).number(), values.getOrNull(5).number())
                }
                fun nextLine() { state.line = state.line.translated(0.0, -state.leading); state.text = state.line }
                suspend fun show(value: PdfValue?) {
                    val string = value as? PdfString ?: return
                    if (!state.inText) return
                    val font = state.font
                    if (font == null) { shown += string.bytes.size; unreadable += string.bytes.size; return }
                    for (glyph in font.decode(string.bytes)) {
                        shown++
                        require(shown <= MAX_SHOWN_GLYPHS) { "PDF glyph limit" }
                        if (shown % 4096 == 0L) yield()
                        val value = glyph.text
                        if (value == null) unreadable++
                        val transform = state.ctm * state.text
                        val start = point(transform.point(0.0, state.rise))
                        val end = point(transform.point(glyph.width * state.size / 1000 * state.scale, state.rise))
                        val top = point(transform.point(0.0, state.rise + state.size))
                        val size = hypot(top.x - start.x, top.y - start.y).coerceIn(0.1, 1000.0)
                        if (!value.isNullOrEmpty() && value.none { it == '\u0000' || it == '\ufffd' } &&
                            end.x >= start.x && abs(end.y - start.y) <= maxOf(0.1, abs(end.x - start.x) * 0.03)) {
                            textBytes += value.length * 3
                            require(textBytes <= ImportLimits.MAX_TEXT_FILE_SIZE && ++glyphCount <= MAX_GLYPHS)
                            require(start.x.isFinite() && start.y.isFinite() && end.x.isFinite() && abs(start.x) < 10_000_000 && abs(start.y) < 10_000_000)
                            glyphs += Positioned(ExtractedDocument.Span(value, start.x, end.x, size, font.bold, font.monospace, abs(state.rise) > state.size * 0.15), start.y)
                        }
                        val advance = (glyph.width * state.size / 1000 + state.charSpace + if (glyph.code == 32) state.wordSpace else 0.0) * state.scale
                        state.text = state.text.translated(advance, 0.0)
                    }
                }
                while (true) {
                    val value = parser.next(references = false) ?: break
                    if (value !is PdfKeyword) {
                        require(operands.size < 10_000)
                        operands += value
                        continue
                    }
                    require(++operators < 2_000_000)
                    if (operators % 4096 == 0) yield()
                    when (value.value) {
                        "q" -> { require(saved.size < 64); saved += state.copy() }
                        "Q" -> if (saved.isNotEmpty()) {
                            val text = state.text; val line = state.line; val inText = state.inText
                            state = saved.removeAt(saved.lastIndex).copy(text = text, line = line, inText = inText)
                        }
                        "cm" -> state.ctm = state.ctm * matrix(operands)
                        "BT" -> { state.text = Matrix(); state.line = Matrix(); state.inText = true }
                        "ET" -> state.inText = false
                        "Tf" -> {
                            val name = operands.firstOrNull().name()
                            val fontDictionary = file.dictionary(file.dictionary(resource?.get("Font"))?.get(name.orEmpty()))
                            state.font = fontDictionary?.let { fonts.getOrPut(it) { PdfFont(file, it) } }
                            state.size = abs(n(1, 12.0)).coerceIn(0.1, 1000.0)
                        }
                        "Tc" -> state.charSpace = n(0)
                        "Tw" -> state.wordSpace = n(0)
                        "Tz" -> state.scale = n(0, 100.0) / 100
                        "TL" -> state.leading = n(0)
                        "Ts" -> state.rise = n(0)
                        "Td", "TD" -> {
                            if (value.value == "TD") state.leading = -n(1)
                            state.line = state.line.translated(n(0), n(1)); state.text = state.line
                        }
                        "Tm" -> { state.text = matrix(operands); state.line = state.text }
                        "T*" -> nextLine()
                        "Tj" -> show(operands.firstOrNull())
                        "TJ" -> operands.firstOrNull().array().forEach { item ->
                            if (item is PdfNumber) state.text = state.text.translated(-item.value / 1000 * state.size * state.scale, 0.0) else show(item)
                        }
                        "'" -> { nextLine(); show(operands.firstOrNull()) }
                        "\"" -> { state.wordSpace = n(0); state.charSpace = n(1); nextLine(); show(operands.getOrNull(2)) }
                        "Do" -> {
                            val objectValue = file.dictionary(resource?.get("XObject"))?.get(operands.firstOrNull().name().orEmpty())
                            val form = file.resolve(objectValue) as? PdfStream
                            if (form?.dictionary?.get("Subtype").name() == "Form") {
                                require(activeForms.add(form!!)) { "Cyclic PDF form" }
                                val transform = if (form.dictionary["Matrix"] != null) matrix(file.array(form.dictionary["Matrix"])) else Matrix()
                                interpret(file.decode(form), file.dictionary(form.dictionary["Resources"]) ?: resource, state.copy(ctm = state.ctm * transform), depth + 1)
                                activeForms.remove(form)
                            }
                        }
                        "BI" -> {
                            // Inline image data is binary, not content syntax. Skip its dictionary and byte range.
                            while (true) {
                                val item = parser.next(references = false) ?: error("Truncated inline image")
                                if (item is PdfKeyword && item.value == "ID") break
                            }
                            var end = parser.indexOf("EI", parser.position)
                            while (end >= 0 && (end == 0 || !(data[end - 1].toInt().toChar().isWhitespace()) ||
                                    data.getOrNull(end + 2)?.toInt()?.toChar()?.isWhitespace() == false)) end = parser.indexOf("EI", end + 2)
                            require(end >= 0)
                            parser.position = end + 2
                        }
                    }
                    operands.clear()
                }
            }
            val contents = file.resolve(dictionary["Contents"])
            val streams = if (contents is PdfArray) contents.values.mapNotNull(file::stream) else listOfNotNull(file.stream(contents))
            val length = streams.sumOf { it.size.toLong() + 1 }
            require(length <= ImportLimits.MAX_IMPORT_SIZE)
            val data = ByteArray(length.toInt())
            var at = 0
            for (stream in streams) { stream.copyInto(data, at); at += stream.size; data[at++] = 10 }
            interpret(data, resources, State())
            pages += Page(buildLines(glyphs), if (rotate in listOf(90, 270)) x1 - x0 else y1 - y0)
            yield()
        }

        suspend fun walk(value: PdfValue?, inheritedResources: PdfDictionary?, inheritedBox: List<PdfValue>, inheritedRotation: Int, depth: Int = 0) {
            require(depth <= 64 && visited.size < 100_000 && value != null && visited.add(value)) { "Cyclic PDF page tree" }
            val dictionary = file.dictionary(value) ?: error("PDF page dictionary")
            val resources = file.dictionary(dictionary["Resources"]) ?: inheritedResources
            val box = file.array(dictionary["MediaBox"]).ifEmpty { inheritedBox }
            val rotation = file.number(dictionary["Rotate"], inheritedRotation.toDouble()).toInt()
            if (dictionary["Type"].name() == "Page") page(dictionary, resources, box, rotation)
            else for (child in file.array(dictionary["Kids"])) walk(child, resources, box, rotation, depth + 1)
        }
        walk(file.catalog()["Pages"], null, listOf(PdfNumber(0.0), PdfNumber(0.0), PdfNumber(612.0), PdfNumber(792.0)), 0)
        require(shown > 0 && unreadable * 2 <= shown) { "PDF has no readable font mapping" }
        val repeated = runningLines(pages)
        return ExtractedDocument(pages.mapIndexed { index, page ->
            val lines = page.lines.filterNot { line ->
                val edge = line.y < page.height * 0.08 || line.y > page.height * 0.92
                val text = line.text.trim()
                edge && (text.all { it.isDigit() } || isPageCount(text, index + 1, pages.size) || signature(line, page.height) in repeated)
            }
            val paragraphs = mutableListOf<ExtractedDocument.Line>()
            lines.forEachIndexed { index, line ->
                val previous = lines.getOrNull(index - 1)
                val size = (line.spans + previous?.spans.orEmpty()).maxOfOrNull { it.size } ?: 12.0
                if (previous != null && line.y - previous.y > size * 2.2) paragraphs += ExtractedDocument.Line(emptyList())
                paragraphs += ExtractedDocument.Line(line.spans)
            }
            ExtractedDocument.Page(paragraphs)
        })
    }

    private fun buildLines(glyphs: List<Positioned>): List<Line> {
        if (glyphs.isEmpty()) return emptyList()
        // Find persistent whitespace bands. A full-width title may cross a gutter; body lines vote on it.
        val left = glyphs.minOf { it.span.start }
        val right = glyphs.maxOf { it.span.end }
        val size = glyphs.map { it.span.size }.sorted().let { it[it.size / 2] }
        val candidates = (1..64).map { left + (right - left) * it / 65 }
        val inkRows = glyphs.map { (it.y / 3).roundToInt() }.toSet()
        val gutter = candidates.filter { it > left + (right - left) * 0.25 && it < left + (right - left) * 0.75 }
            .mapNotNull { x ->
                val band = size * 1.5
                val crossing = glyphs.filter { it.span.end > x - band && it.span.start < x }.map { (it.y / 3).roundToInt() }.toSet().size
                val both = glyphs.count { it.span.end <= x - band } > glyphs.size / 5 && glyphs.count { it.span.start >= x } > glyphs.size / 5
                val end = glyphs.filter { it.span.size <= size * 1.2 && it.span.end <= x }.maxOfOrNull { it.span.end } ?: left
                if (both && crossing <= inkRows.size / 5) Triple(x, crossing, x - end) else null
            }.sortedWith(compareBy<Triple<Double, Int, Double>> { it.second }.thenByDescending { it.third }).firstOrNull()?.first
        fun column(items: List<Positioned>): List<Line> {
            val groups = mutableListOf<MutableList<Positioned>>()
            for (glyph in items.sortedBy { it.y }) {
                val group = groups.lastOrNull()
                if (group == null || abs(group.first().y - glyph.y) > minOf(group.first().span.size, glyph.span.size) * 0.22) groups += mutableListOf(glyph)
                else group += glyph
            }
            return groups.map { group ->
                val spans = mutableListOf<ExtractedDocument.Span>()
                for (glyph in group.sortedBy { it.span.start }) {
                    val previous = spans.lastOrNull()
                    val span = glyph.span
                    // Some producers paint a shadow/duplicate over the same glyph.
                    if (previous != null && previous.text == span.text && abs(previous.start - span.start) < 0.1) continue
                    val gap = if (previous == null) 0.0 else span.start - previous.end
                    if (gap > span.size * 0.25 && previous?.text?.endsWith(' ') != true && !span.text.startsWith(' ')) {
                        val count = if (span.isMonospace) (gap / (span.size * 0.6)).roundToInt().coerceIn(1, 1000) else 1
                        spans += ExtractedDocument.Span(" ".repeat(count), previous!!.end, span.start, span.size, isMonospace = span.isMonospace)
                    }
                    spans += span
                }
                Line(spans, group.first().y)
            }
        }
        if (gutter == null) return column(glyphs)
        val spanning = glyphs.filter { it.span.end > gutter - size * 1.5 && it.span.start < gutter }
            .map { it.y }.distinct().sorted()
        val result = mutableListOf<Line>()
        var remaining = glyphs
        fun columns(items: List<Positioned>): List<Line> {
            val (first, second) = items.partition { it.span.start < gutter }
            return column(first) + column(second)
        }
        for (y in spanning) {
            result += columns(remaining.filter { it.y < y - size * 0.22 })
            result += column(remaining.filter { abs(it.y - y) <= size * 0.22 })
            remaining = remaining.filter { it.y > y + size * 0.22 }
        }
        result += columns(remaining)
        return result
    }

    private fun signature(line: Line, height: Double): String {
        val text = line.text.replace(Regex("[0-9]+"), "#").trim()
        val x = ((line.spans.firstOrNull()?.start ?: 0.0) / 5).toInt()
        val y = (line.y / height * 100).toInt()
        return "$x:$y:$text"
    }
    private fun runningLines(pages: List<Page>): Set<String> {
        if (pages.size < 2) return emptySet()
        val counts = mutableMapOf<String, Int>()
        for (page in pages) page.lines.filter { it.y < page.height * 0.08 || it.y > page.height * 0.92 }
            .map { signature(it, page.height) }.toSet().forEach { counts[it] = (counts[it] ?: 0) + 1 }
        return counts.filterValues { it >= 2 && it * 2 >= pages.size }.keys
    }

    /**
     * A footer like `2 / 3` is only taken for a page number when both numbers are this page's own: a chord sheet's first
     * line near the top of the page is often a bare time signature (`3/4`, `6/8`), which a looser match would drop.
     */
    private fun isPageCount(text: String, page: Int, pageCount: Int) =
        pageCountPattern.matchEntire(text)?.destructured?.let { (number, total) -> number == "$page" && total == "$pageCount" } ?: false

    private val pageCountPattern = Regex("([0-9]+)\\s*/\\s*([0-9]+)")
}
