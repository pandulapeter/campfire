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
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/** Glyphs positioned on a page, set into lines, with the columns of a page read one after another. */
internal object PdfLineLayout {
    data class Positioned(val span: ExtractedDocument.Span, val y: Double)
    data class Line(val spans: List<ExtractedDocument.Span>, val y: Double) {
        val text get() = spans.joinToString("") { it.text }
    }

    fun buildLines(glyphs: List<Positioned>, charge: (Int) -> Unit): List<Line> {
        if (glyphs.isEmpty()) return emptyList()
        val size = glyphs.map { it.span.size }.sorted().let { it[it.size / 2] }
        val band = size * GUTTER_WIDTH
        val gutter = gutter(glyphs, band)
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
                        charge(count)
                        spans += ExtractedDocument.Span(" ".repeat(count), previous!!.end, span.start, span.size, isMonospace = span.isMonospace)
                    }
                    spans += span
                }
                Line(spans, group.first().y)
            }
        }
        if (gutter == null) return column(glyphs)
        val spanning = glyphs.filter { it.span.end > gutter - band && it.span.start < gutter }
            .map { it.y }.distinct().sorted()
        val result = mutableListOf<Line>()
        // Each side is searched for a gutter of its own, since a page of three or four columns is split at one of
        // its gutters first and still holds the others on either side of it.
        fun columns(items: List<Positioned>): List<Line> {
            val (first, second) = items.partition { it.span.start < gutter }
            return if (first.isEmpty() || second.isEmpty()) column(items) else buildLines(first, charge) + buildLines(second, charge)
        }
        // The rows are cut out of one sorted pass rather than by filtering every glyph again for each of them, which is the
        // same partition, since both thresholds grow with the row.
        val sorted = glyphs.sortedBy { it.y }
        val heights = sorted.map { it.y }
        var from = 0
        for (y in spanning) {
            val start = maxOf(from, heights.countBelow(y - size * 0.22))
            val end = maxOf(start, heights.countAtMost(y + size * 0.22))
            result += columns(sorted.subList(from, start))
            result += column(sorted.subList(start, end))
            from = end
        }
        result += columns(sorted.subList(from, sorted.size))
        return result
    }

    /**
     * The right edge of the whitespace band between two columns, if [glyphs] have one: a band [band] wide in the middle
     * half of their width that hardly any line crosses — a title over both columns may — with a fair share of the text
     * on either side. A band no line crosses at all is a gutter with less than that on one side, as long as the text
     * right of it reads like a column, a few lines starting at one edge: it is the last column of a page that ends early. Every
     * position is tried, not a sample of them, since a column filled to its edge leaves a gap barely wider than the
     * band; a line blocks the positions its ink, merged across gaps too narrow to hold the band, would put in the band.
     */
    private fun gutter(glyphs: List<Positioned>, band: Double): Double? {
        val left = glyphs.minOf { it.span.start }
        val right = glyphs.maxOf { it.span.end }
        val size = glyphs.map { it.span.size }.sorted().let { it[it.size / 2] }
        val step = maxOf(0.5, (right - left) / MAX_GUTTER_POSITIONS)
        val count = ((right - left) / step).toInt() + 1
        val rows = glyphs.groupBy { (it.y / 3).roundToInt() }.values.map { row -> row.sortedBy { it.span.start } }
        val blocked = IntArray(count + 1)
        for (row in rows) {
            var start = row.first().span.start
            var end = row.first().span.end
            fun block() {
                val from = (floor((start - left) / step) + 1).toInt().coerceAtLeast(0)
                val to = (ceil((end + band - left) / step) - 1).toInt().coerceAtMost(count - 1)
                if (from <= to) { blocked[from]++; blocked[to + 1]-- }
            }
            for (glyph in row.drop(1)) {
                if (glyph.span.start - end < band) end = maxOf(end, glyph.span.end) else { block(); start = glyph.span.start; end = glyph.span.end }
            }
            block()
        }
        val ends = glyphs.map { it.span.end }.sorted()
        val starts = glyphs.map { it.span.start }.sorted()
        val normalEnds = glyphs.filter { it.span.size <= size * 1.2 }.map { it.span.end }.sorted()
        val rowEnds = rows.map { row -> row.minOf { it.span.end } }.sorted()
        val rowStarts = rows.map { row -> row.maxOf { it.span.start } }.sorted()
        // The gap left of the band, which picks the widest of several bands that are crossed as rarely.
        fun gap(x: Double) = x - (normalEnds.getOrNull(normalEnds.countAtMost(x) - 1) ?: left)
        fun isColumn(x: Double): Boolean {
            val firsts = rows.mapNotNull { row -> row.firstOrNull { it.span.start >= x }?.span?.start }
            val edge = firsts.minOrNull() ?: return false
            return firsts.count { it - edge < 1.0 } * 2 >= firsts.size
        }
        var crossing = 0
        var best: Double? = null
        var bestCrossing = 0
        var fallback: Double? = null
        for (index in 0 until count) {
            crossing += blocked[index]
            val x = left + index * step
            if (x <= left + (right - left) * 0.25 || x >= left + (right - left) * 0.75) continue
            val leftCount = ends.countAtMost(x - band)
            val rightCount = starts.size - starts.countBelow(x)
            if (leftCount > glyphs.size / 5 && rightCount > glyphs.size / 5 && crossing <= rows.size / 5) {
                if (best == null || crossing < bestCrossing || crossing == bestCrossing && gap(x) > gap(best)) { best = x; bestCrossing = crossing }
            } else if (crossing == 0 && rowEnds.countAtMost(x - band) >= 2 && rowStarts.size - rowStarts.countBelow(x) >= 2) {
                if (fallback == null || gap(x) > gap(fallback)) fallback = x
            }
        }
        return best ?: fallback?.takeIf(::isColumn)
    }

    private fun List<Double>.countAtMost(value: Double): Int {
        var low = 0
        var high = size
        while (low < high) { val middle = (low + high) / 2; if (this[middle] <= value) low = middle + 1 else high = middle }
        return low
    }

    private fun List<Double>.countBelow(value: Double): Int {
        var low = 0
        var high = size
        while (low < high) { val middle = (low + high) / 2; if (this[middle] < value) low = middle + 1 else high = middle }
        return low
    }

    /**
     * The narrowest whitespace between columns, in ems of the text: well above the space between two words, and below
     * the gutter Campfire's own export leaves at its largest text size, 18 points at 20.
     */
    private const val GUTTER_WIDTH = 0.8

    /** How many positions a gutter is looked for at, every half point up to this, which bounds the work on a wide page. */
    private const val MAX_GUTTER_POSITIONS = 4096.0
}
