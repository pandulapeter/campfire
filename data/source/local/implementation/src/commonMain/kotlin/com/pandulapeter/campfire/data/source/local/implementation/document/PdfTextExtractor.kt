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
import com.pandulapeter.campfire.data.source.local.implementation.document.PdfContentInterpreter.Point
import com.pandulapeter.campfire.data.source.local.implementation.document.PdfContentInterpreter.State
import com.pandulapeter.campfire.data.source.local.implementation.document.PdfLineLayout.Line
import com.pandulapeter.campfire.data.source.local.implementation.document.PdfLineLayout.buildLines
import com.pandulapeter.campfire.data.source.local.implementation.document.PdfRunningLines.isPageCount
import com.pandulapeter.campfire.data.source.local.implementation.document.PdfRunningLines.runningLines
import com.pandulapeter.campfire.data.source.local.implementation.document.PdfRunningLines.signature
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.yield
import kotlin.math.abs

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
    /**
     * The content interpreted and the stream input read in one document, re-reading included. Every distinct stream fits
     * in the decoded cap of [ImportLimits.MAX_IMPORT_SIZE], so this leaves about three times that for header and footer
     * forms and for contents shared between pages before the document is taken for a hostile one.
     */
    internal const val MAX_INTERPRETED_BYTES = 4L * ImportLimits.MAX_IMPORT_SIZE
    data class Page(val lines: List<Line>, val height: Double)

    suspend fun extract(bytes: ByteArray): ExtractedDocument {
        val file = PdfFile(bytes)
        val pages = mutableListOf<Page>()
        val fonts = mutableMapOf<PdfDictionary, PdfFont>()
        val failedFonts = mutableSetOf<PdfDictionary>()
        val visited = mutableSetOf<PdfValue>()
        val budget = PdfContentBudget()
        var failedPages = 0

        // A malformed font costs the text shown in it, which counts as unreadable, and is not built again on every Tf.
        fun font(dictionary: PdfDictionary): PdfFont? {
            if (dictionary in failedFonts) return null
            fonts[dictionary]?.let { return it }
            return try {
                PdfFont(file, dictionary).also { fonts[dictionary] = it }
            } catch (exception: PdfLimitException) {
                throw exception
            } catch (exception: Exception) {
                if (file.isEncrypted) throw exception
                failedFonts += dictionary
                null
            }
        }

        suspend fun readPage(dictionary: PdfDictionary, resources: PdfDictionary?, box: List<PdfValue>, rotation: Int): Page {
            val bounds = box.map { file.number(it) }
            require(bounds.size == 4 && bounds.all { it.isFinite() && abs(it) < 1_000_000 })
            // A producer that writes 359 means upright, so a rotation that is no multiple of 90 is read as the nearest one.
            val rotate = (((rotation % 360) + 360) % 360 + 45) / 90 * 90 % 360
            // A rectangle may name any two opposite corners, in either order.
            val x0 = minOf(bounds[0], bounds[2]); val y0 = minOf(bounds[1], bounds[3])
            val x1 = maxOf(bounds[0], bounds[2]); val y1 = maxOf(bounds[1], bounds[3])
            require(x1 > x0 && y1 > y0)
            fun point(point: Point) = when (rotate) {
                90 -> Point(point.y - y0, point.x - x0)
                180 -> Point(x1 - point.x, point.y - y0)
                270 -> Point(y1 - point.y, x1 - point.x)
                else -> Point(point.x - x0, y1 - point.y)
            }
            val interpreter = PdfContentInterpreter(file = file, budget = budget, font = ::font, point = ::point)
            val contents = file.resolve(dictionary["Contents"])
            val streams = if (contents is PdfArray) contents.values.mapNotNull(file::stream) else listOfNotNull(file.stream(contents))
            val length = streams.sumOf { it.size.toLong() + 1 }
            requireWithinLimit(length <= ImportLimits.MAX_IMPORT_SIZE) { "PDF page content limit" }
            budget.chargeWork(length)
            val data = ByteArray(length.toInt())
            var at = 0
            for (stream in streams) { stream.copyInto(data, at); at += stream.size; data[at++] = 10 }
            interpreter.interpret(data, resources, State())
            return Page(buildLines(interpreter.glyphs, budget::chargeText), if (rotate in listOf(90, 270)) x1 - x0 else y1 - y0)
        }

        // A malformed page costs only itself: its slot stays, empty, so that page numbers and the running lines still
        // count it, and it counts as unreadable, so that a document of one bad page is still unreadable as a whole.
        // Budgets and encryption are the document's, and the next page would only run into them again.
        suspend fun page(dictionary: PdfDictionary, resources: PdfDictionary?, box: List<PdfValue>, rotation: Int) {
            // A songbook whose tail is silently left out is worse than one that is reported as unreadable.
            requireWithinLimit(pages.size < 2000) { "PDF page limit" }
            pages += try {
                readPage(dictionary, resources, box, rotation)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: PdfLimitException) {
                throw exception
            } catch (exception: Exception) {
                if (file.isEncrypted) throw exception
                budget.shown++; budget.unreadable++; failedPages++
                Page(emptyList(), 792.0)
            }
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
        require(budget.shown > 0 && budget.unreadable * 2 <= budget.shown) { "PDF has no readable font mapping" }
        require(failedPages * 2 <= pages.size) { "Most PDF pages are unreadable" }
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

    /**
     * Whether [text] holds half of a surrogate pair without the other half, which a broken font map can produce and
     * which becomes a `?` once the song is written as UTF-8.
     */
    internal fun hasLoneSurrogate(text: String): Boolean {
        var index = 0
        while (index < text.length) {
            val character = text[index]
            if (character.isHighSurrogate()) {
                if (index + 1 >= text.length || !text[index + 1].isLowSurrogate()) return true
                index += 2
            } else {
                if (character.isLowSurrogate()) return true
                index++
            }
        }
        return false
    }
}
