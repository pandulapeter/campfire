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

import com.pandulapeter.campfire.data.model.domain.ExtractedDocument
import com.pandulapeter.campfire.data.model.domain.ImportLimits
import com.pandulapeter.campfire.data.formats.document.PdfLineLayout.Positioned
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.yield
import kotlin.math.abs
import kotlin.math.hypot

/** What one document has used up of its budgets so far, shared by every page and form read from it. */
internal class PdfContentBudget {
    var shown = 0L
    var unreadable = 0L
    var textBytes = 0L
    var operators = 0
    var glyphCount = 0
    private var interpretedBytes = 0L

    /**
     * The spaces that bridge a gap between glyphs are text too: a monospace font at a tenth of a point asks for a
     * thousand of them per glyph, which would turn the glyph budget into gigabytes of padding.
     */
    fun chargeText(characters: Int) {
        textBytes += characters * 3L
        requireWithinLimit(textBytes <= ImportLimits.MAX_TEXT_FILE_SIZE) { "PDF text too large" }
    }

    /**
     * A form invoked again and contents shared between pages are read again, so they are paid for every time. Whitespace
     * and comments are no operators, so this is also where a long run of them yields.
     */
    suspend fun chargeWork(bytes: Long) {
        val before = interpretedBytes
        interpretedBytes += bytes
        requireWithinLimit(interpretedBytes <= PdfTextExtractor.MAX_INTERPRETED_BYTES) { "PDF content work limit" }
        if (interpretedBytes / WORK_YIELD_INTERVAL != before / WORK_YIELD_INTERVAL) yield()
    }

    private companion object {
        const val WORK_YIELD_INTERVAL = 4L shl 20
    }
}

/**
 * Runs the content of one page, and of the forms it invokes, collecting the [glyphs] it shows. [point] takes a point
 * of the page's user space to where it is read, rotation included.
 */
internal class PdfContentInterpreter(
    private val file: PdfFile,
    private val budget: PdfContentBudget,
    private val font: (PdfDictionary) -> PdfFont?,
    private val point: (Point) -> Point,
) {
    val glyphs = mutableListOf<Positioned>()
    private val activeForms = mutableSetOf<PdfStream>()

    /**
     * The glyphs shown since [from] (forms run inside the marked content included) replaced by the [actual] text a
     * producer gave them, which is how a right-to-left run written in logical order reads in the right order. Text
     * over glyphs on more than one baseline is left alone: Word and InDesign put a whole word over one hyphenated
     * across a line break, and a span at the first line's baseline reaching back to the second line's margin would
     * garble both lines.
     */
    private fun replace(from: Int, actual: PdfString) {
        if (from >= glyphs.size) return
        val replaced = glyphs.subList(from, glyphs.size)
        if (replaced.maxOf { it.y } - replaced.minOf { it.y } > replaced.minOf { it.span.size } * 0.22) return
        val bytes = actual.bytes
        if (bytes.size > MAX_ACTUAL_TEXT_BYTES) return
        val isUtf16 = bytes.size >= 2 && bytes[0] == 0xfe.toByte() && bytes[1] == 0xff.toByte()
        if (isUtf16 && bytes.size % 2 != 0) return
        val text = if (isUtf16) PdfFont.utf16(bytes) else bytes.latin1()
        // An empty replacement is how a producer marks a hyphen or a decoration as not being text.
        if (text.isEmpty()) { replaced.clear(); return }
        if (text.any { it == '\u0000' || it == '\ufffd' } || PdfTextExtractor.hasLoneSurrogate(text)) return
        // The glyphs replaced stay charged, so a replacement around every glyph costs at most twice the text.
        budget.textBytes += text.length * 3
        requireWithinLimit(budget.textBytes <= ImportLimits.MAX_TEXT_FILE_SIZE && ++budget.glyphCount <= PdfTextExtractor.MAX_GLYPHS) { "PDF text too large" }
        val first = replaced.first()
        val span = first.span.copy(text = text, start = replaced.minOf { it.span.start }, end = replaced.maxOf { it.span.end })
        replaced.clear()
        glyphs += Positioned(span, first.y)
    }

    suspend fun interpret(data: ByteArray, resource: PdfDictionary?, initial: State, depth: Int = 0) {
        require(depth <= 32)
        val parser = PdfSyntax(data)
        var state = initial.copy()
        val saved = mutableListOf<State>()
        val operands = mutableListOf<PdfValue>()
        // Where each marked-content sequence open in this content began, and the text that stands for it, if any.
        val marked = mutableListOf<Pair<Int, PdfString?>>()
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
            if (font == null) { budget.shown += string.bytes.size; budget.unreadable += string.bytes.size; return }
            for (glyph in font.decode(string.bytes)) {
                budget.shown++
                requireWithinLimit(budget.shown <= PdfTextExtractor.MAX_SHOWN_GLYPHS) { "PDF glyph limit" }
                if (budget.shown % 4096 == 0L) yield()
                val value = glyph.text
                if (value == null) budget.unreadable++
                val transform = state.ctm * state.text
                val start = point(transform.point(0.0, state.rise))
                val end = point(transform.point(glyph.width * state.size / 1000 * state.scale, state.rise))
                val top = point(transform.point(0.0, state.rise + state.size))
                val size = hypot(top.x - start.x, top.y - start.y).coerceIn(0.1, 1000.0)
                if (!value.isNullOrEmpty() && value.none { it == '\u0000' || it == '\ufffd' } && !PdfTextExtractor.hasLoneSurrogate(value) &&
                    end.x >= start.x && abs(end.y - start.y) <= maxOf(0.1, abs(end.x - start.x) * 0.03)) {
                    budget.textBytes += value.length * 3
                    requireWithinLimit(budget.textBytes <= ImportLimits.MAX_TEXT_FILE_SIZE && ++budget.glyphCount <= PdfTextExtractor.MAX_GLYPHS) { "PDF text too large" }
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
            requireWithinLimit(++budget.operators < 2_000_000) { "PDF operator limit" }
            if (budget.operators % 4096 == 0) yield()
            when (value.value) {
                "q" -> { require(saved.size < 64); saved += state.copy() }
                "Q" -> if (saved.isNotEmpty()) {
                    val text = state.text; val line = state.line; val inText = state.inText
                    state = saved.removeAt(saved.lastIndex).copy(text = text, line = line, inText = inText)
                }
                "cm" -> state.ctm = state.ctm * matrix(operands)
                "BT" -> { state.text = Matrix(); state.line = Matrix(); state.inText = true }
                "ET" -> state.inText = false
                "BMC" -> marked += glyphs.size to null
                // A property list named rather than written inline is ordinary marked content.
                "BDC" -> marked += glyphs.size to ((operands.getOrNull(1) as? PdfDictionary)?.get("ActualText") as? PdfString)
                "EMC" -> if (marked.isNotEmpty()) {
                    val (from, actual) = marked.removeAt(marked.lastIndex)
                    if (actual != null) replace(from, actual)
                }
                "Tf" -> {
                    val name = operands.firstOrNull().name()
                    val fontDictionary = file.dictionary(file.dictionary(resource?.get("Font"))?.get(name.orEmpty()))
                    state.font = fontDictionary?.let(font)
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
                        // The cycle is tested outside the try, whose finally would otherwise remove the entry of the
                        // invocation of the same form that is still running further up.
                        if (!activeForms.add(form!!)) { budget.shown++; budget.unreadable++ }
                        else try {
                            val transform = if (form.dictionary["Matrix"] != null) matrix(file.array(form.dictionary["Matrix"])) else Matrix()
                            val formData = file.decode(form)
                            budget.chargeWork(formData.size.toLong())
                            interpret(formData, file.dictionary(form.dictionary["Resources"]) ?: resource, state.copy(ctm = state.ctm * transform), depth + 1)
                        } catch (exception: CancellationException) {
                            throw exception
                        } catch (exception: PdfLimitException) {
                            throw exception
                        } catch (exception: Exception) {
                            if (file.isEncrypted) throw exception
                            budget.shown++; budget.unreadable++
                        } finally {
                            activeForms.remove(form)
                        }
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

    data class Point(val x: Double, val y: Double)
    data class Matrix(val a: Double = 1.0, val b: Double = 0.0, val c: Double = 0.0, val d: Double = 1.0, val e: Double = 0.0, val f: Double = 0.0) {
        fun point(x: Double, y: Double) = Point(a * x + c * y + e, b * x + d * y + f)
        operator fun times(other: Matrix) = Matrix(
            a * other.a + c * other.b, b * other.a + d * other.b,
            a * other.c + c * other.d, b * other.c + d * other.d,
            a * other.e + c * other.f + e, b * other.e + d * other.f + f,
        )
        fun translated(x: Double, y: Double) = this * Matrix(e = x, f = y)
    }
    data class State(
        var ctm: Matrix = Matrix(), var text: Matrix = Matrix(), var line: Matrix = Matrix(),
        var font: PdfFont? = null, var size: Double = 12.0, var charSpace: Double = 0.0,
        var wordSpace: Double = 0.0, var scale: Double = 1.0, var leading: Double = 0.0, var rise: Double = 0.0,
        var inText: Boolean = false,
    )

    private companion object {
        const val MAX_ACTUAL_TEXT_BYTES = 64 shl 10
    }
}
