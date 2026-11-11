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
import com.pandulapeter.campfire.chordpro.language.WritingDirection
import com.pandulapeter.campfire.chordpro.language.writingDirectionOf
import com.pandulapeter.campfire.chordpro.convert.ChordSheet
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/** Glyphs positioned on a page, set into lines, with the columns of a page read one after another. */
internal object PdfLineLayout {
    data class Positioned(val span: ChordSheet.Span, val y: Double)
    /** A line at [y], set in [column]: null for a line crossing the gutter between two, which belongs to neither. */
    data class Line(val spans: List<ChordSheet.Span>, val y: Double, val column: PdfBidi.Column? = null) {
        val text get() = spans.joinToString("") { it.text }
    }

    fun buildLines(glyphs: List<Positioned>, charge: (Int) -> Unit): List<Line> {
        if (glyphs.isEmpty()) return emptyList()
        val size = glyphs.map { it.span.size }.sorted().let { it[it.size / 2] }
        val band = size * GUTTER_WIDTH
        val gutter = gutter(glyphs, band)
        if (gutter == null) return rows(glyphs, charge)
        val spanning = glyphs.filter { it.span.end > gutter - band && it.span.start < gutter }
            .map { it.y }.distinct().sorted()
        val result = mutableListOf<Line>()
        // Each side is searched for a gutter of its own, since a page of three or four columns is split at one of
        // its gutters first and still holds the others on either side of it.
        fun columns(items: List<Positioned>): List<Line> {
            val (left, right) = items.partition { it.span.start < gutter }
            if (left.isEmpty() || right.isEmpty()) return rows(items, charge)
            // A page of right-to-left text is read from its right column, as Campfire's export fills a Hebrew song's.
            val isRightToLeft = writingDirectionOf(items.joinToString("") { it.span.text }) == WritingDirection.RIGHT_TO_LEFT
            val (first, second) = if (isRightToLeft) right to left else left to right
            val firstLines = buildLines(first, charge)
            val secondLines = buildLines(second, charge)
            val over = overGutter(secondLines, firstLines, isRightOfGutter = !isRightToLeft, band = band)
            return over.sortedBy { it.y } + firstLines + secondLines.filterNot { it in over }
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
            result += rows(sorted.subList(start, end), charge).map { it.copy(column = null) }
            from = end
        }
        result += columns(sorted.subList(from, sorted.size))
        return result
    }

    /**
     * The lines of the column read second, [lines], set above every line of the column read first, [other], that reach
     * into the gutter by more than a [band] past where the rest of their column starts: a title and credit centred over
     * both columns, which a gutter left of them puts in the second column, though they are read before both.
     */
    private fun overGutter(lines: List<Line>, other: List<Line>, isRightOfGutter: Boolean, band: Double): Set<Line> {
        val top = other.minOfOrNull { it.y } ?: return emptySet()
        val (above, below) = lines.filter { line -> line.spans.any { it.text.isNotBlank() } }.partition { it.y < top }
        if (below.isEmpty()) return emptySet()
        return if (isRightOfGutter) {
            val edge = below.minOf { it.spans.first().start }
            above.filter { it.spans.first().start < edge - band }.toSet()
        } else {
            val edge = below.maxOf { it.spans.last().end }
            above.filter { it.spans.last().end > edge + band }.toSet()
        }
    }

    /**
     * [items] set into lines by their baselines alone, with no column searched for: how a header or a footer reads, its
     * parts at both margins on one line. A line holding right-to-left text is put into reading order ([PdfBidi]), in the
     * direction the side it is aligned to among [items] says, else the one most letters of their lines that are not chord
     * rows read in: a Hebrew sheet's chord names would otherwise outvote its lyrics.
     */
    fun rows(items: List<Positioned>, charge: (Int) -> Unit): List<Line> {
        if (items.isEmpty()) return emptyList()
        val groups = mutableListOf<MutableList<Positioned>>()
        for (glyph in items.sortedBy { it.y }) {
            val group = groups.lastOrNull()
            if (group == null || abs(group.first().y - glyph.y) > minOf(group.first().span.size, glyph.span.size) * 0.22) groups += mutableListOf(glyph)
            else group += glyph
        }
        val lines = attachRaised(groups).map { (y, group) ->
            val spans = mutableListOf<ChordSheet.Span>()
            for (glyph in group.sortedBy { it.span.start }) {
                val previous = spans.lastOrNull()
                val span = glyph.span
                // Some producers paint a shadow/duplicate over the same glyph.
                if (previous != null && previous.text == span.text && abs(previous.start - span.start) < 0.1) continue
                val gap = if (previous == null) 0.0 else span.start - previous.end
                if (gap > span.size * 0.25 && previous?.text?.endsWith(' ') != true && !span.text.startsWith(' ')) {
                    val count = if (span.isMonospace) (gap / (span.size * 0.6)).roundToInt().coerceIn(1, 1000) else 1
                    charge(count)
                    spans += ChordSheet.Span(" ".repeat(count), previous!!.end, span.start, span.size, isMonospace = span.isMonospace)
                }
                spans += span
            }
            Line(spans, y)
        }
        val column = PdfBidi.Column(
            left = items.minOf { it.span.start },
            right = items.maxOf { it.span.end },
            direction = writingDirectionOf(lines.map { it.text }.filterNot(ChordSheetConverter::isChordLine).joinToString("\n")),
        )
        return lines.map { line -> line.copy(spans = PdfBidi.readingOrder(line.spans, column), column = column) }
    }

    /**
     * The baselines and glyphs of [groups] (in baseline order) with each superscript or subscript run joined to the line
     * it is set on, where it belongs to the word it follows (`2nd`), never a line of its own: a run in smaller type
     * within half an em of a neighbouring line's baseline, each glyph of it starting where that line's text or the run
     * itself ends and over none of that line's letters. A chord stands over its syllable, so a chord row set tightly
     * above its lyric stays a row of its own.
     */
    private fun attachRaised(groups: List<List<Positioned>>): List<Pair<Double, List<Positioned>>> {
        val lines = groups.map { it.toMutableList() }
        val baselines = groups.map { it.first().y }
        // Linked rather than removed from a list, which would move every line after a run each time one joins its line.
        val previous = IntArray(lines.size) { it - 1 }
        val next = IntArray(lines.size) { if (it + 1 < lines.size) it + 1 else -1 }
        val isJoined = BooleanArray(lines.size)
        var work = 0L
        val charge = { comparisons: Long -> work += comparisons; work <= MAX_RAISED_WORK }
        var index = if (lines.isEmpty()) -1 else 0
        while (index >= 0) {
            val run = lines[index]
            val host = listOf(next[index], previous[index]).firstOrNull { it >= 0 && isRaisedOn(run, baselines[it], lines[it], charge) }
            if (host != null) {
                lines[host] += run
                isJoined[index] = true
                if (previous[index] >= 0) next[previous[index]] = next[index]
                if (next[index] >= 0) previous[next[index]] = previous[index]
            }
            index = next[index]
        }
        return lines.indices.filterNot { isJoined[it] }.map { baselines[it] to lines[it] }
    }

    private fun isRaisedOn(run: List<Positioned>, y: Double, glyphs: List<Positioned>, charge: (Long) -> Boolean): Boolean {
        val size = glyphs.maxOf { it.span.size }
        if (run.any { it.span.size > size * RAISED_SIZE } || run.any { abs(it.y - y) > size * RAISED_OFFSET }) return false
        // Every glyph of the run is compared with every one of the line's, so a page setting thousands of runs beside one
        // line pays for the comparisons; once [MAX_RAISED_WORK] is spent, a run stays a line of its own.
        if (!charge(run.size.toLong() * (glyphs.size + run.size))) return false
        val ink = glyphs.filter { it.span.text.isNotBlank() }
        val ends = glyphs.map { it.span.end }.toMutableList()
        return run.sortedBy { it.span.start }.all { glyph ->
            val tolerance = size * RAISED_ATTACHMENT
            ink.none { it.span.start < glyph.span.end - tolerance && it.span.end > glyph.span.start + tolerance } &&
                ends.any { abs(glyph.span.start - it) <= tolerance }.also { if (it) ends += glyph.span.end }
        }
    }

    /**
     * The right edge of the whitespace band between two columns, if [glyphs] have one: a band [band] wide in the middle
     * half of their width that hardly any line crosses — a title over both columns may — with a fair share of the text
     * on either side, counted in characters, since a span carrying a whole line (Campfire's right-to-left runs) is as
     * much text as its letters, and only from ink clear of the band: a line crossing it belongs to neither column, and
     * its two halves would let a narrow column's lyric and capo note, both wider than the gap in the chord row between
     * them, pass for a second column beside that gap. A band no line crosses at all is a gutter with less than that on
     * one side, as long as the text right of it reads like a column, most of its lines starting at one edge: it is the
     * last column of a page that ends early. Such a band wins over one a few lines cross, which a heading wider than the first column's lyrics leaves
     * inside that column when the second one is too short for a fair share. Every position is tried, not a sample of
     * them, since a column filled to its edge leaves a gap barely wider than the band; a line blocks the positions its
     * ink, merged across gaps too narrow to hold the band, would put in the band.
     */
    private fun gutter(glyphs: List<Positioned>, band: Double): Double? {
        val left = glyphs.minOf { it.span.start }
        val right = glyphs.maxOf { it.span.end }
        val size = glyphs.map { it.span.size }.sorted().let { it[it.size / 2] }
        val step = maxOf(0.5, (right - left) / MAX_GUTTER_POSITIONS)
        val count = ((right - left) / step).toInt() + 1
        val rows = glyphs.groupBy { (it.y / 3).roundToInt() }.values.map { row -> row.sortedBy { it.span.start } }
        // A heading over the columns or a footer under them crosses the gutter without belonging to either side, so the
        // fair share each side must hold leaves it out; a line crossing among the columns' rows still counts against them.
        val edgeRows = setOf(rows.minBy { it.first().y }, rows.maxBy { it.first().y })
        val segments = mutableListOf<Segment>()
        for (row in rows) {
            var start = row.first().span.start
            var end = row.first().span.end
            var characters = 0
            fun block() {
                val from = (floor((start - left) / step) + 1).toInt().coerceAtLeast(0)
                val to = (ceil((end + band - left) / step) - 1).toInt().coerceAtMost(count - 1)
                segments += Segment(start, end, characters, row.first().y, from, to, row in edgeRows)
            }
            for (glyph in row) {
                if (characters > 0 && glyph.span.start - end >= band) { block(); start = glyph.span.start; characters = 0 }
                end = maxOf(end, glyph.span.end)
                characters += glyph.span.text.length.coerceAtLeast(1)
            }
            block()
        }
        val byEnd = segments.sortedBy { it.end }
        val ends = byEnd.map { it.end }
        val charactersByEnd = byEnd.runningFold(0) { total, segment -> total + segment.characters }
        val byStart = segments.sortedBy { it.start }
        val starts = byStart.map { it.start }
        val charactersByStart = byStart.runningFold(0) { total, segment -> total + segment.characters }
        // A line above all the ink right of a band reads before that side's column whether it crosses the band or not, as
        // its heading or one over both columns, so of two bands crossed as often the one cutting such a line wins: a key
        // printed over the middle of the page is cut rather than a column's longest chord row. The top of the ink right of
        // a position only falls further down the page the further right the position is.
        val topsByStart = byStart.map { it.y }.asReversed().runningReduce { top, y -> minOf(top, y) }.asReversed()
        val inkRight = (0 until count).count { index -> left + index * step <= starts.last() }
        val tops = List(inkRight) { index -> topsByStart[starts.countBelow(left + index * step)] }
        val blocked = IntArray(count + 1)
        val blockedCharacters = IntArray(count + 1)
        val blockedEdgeCharacters = IntArray(count + 1)
        val blockedOverRight = IntArray(count + 1)
        fun add(counts: IntArray, from: Int, to: Int, amount: Int) {
            if (from <= to) { counts[from] += amount; counts[to + 1] -= amount }
        }
        for (segment in segments) {
            add(blocked, segment.from, segment.to, 1)
            add(blockedCharacters, segment.from, segment.to, segment.characters)
            if (segment.isEdge) add(blockedEdgeCharacters, segment.from, segment.to, segment.characters)
            add(blockedOverRight, maxOf(segment.from, tops.countAtMost(segment.y)), minOf(segment.to, inkRight - 1), 1)
        }
        val characters = charactersByEnd.last()
        val normalEnds = glyphs.filter { it.span.size <= size * 1.2 }.map { it.span.end }.sorted()
        val rowEnds = rows.map { row -> row.minOf { it.span.end } }.sorted()
        val rowStarts = rows.map { row -> row.maxOf { it.span.start } }.sorted()
        // The gap left of the band, which picks the widest of several bands that are crossed as rarely.
        fun gap(x: Double) = x - (normalEnds.getOrNull(normalEnds.countAtMost(x) - 1) ?: left)
        fun isColumn(x: Double): Boolean {
            val firsts = rows.mapNotNull { row -> row.firstOrNull { it.span.start >= x }?.span?.start }
            // The most common start rather than the leftmost, since a page number centred under the columns starts left of them.
            return firsts.maxOfOrNull { edge -> firsts.count { abs(it - edge) < 1.0 } }?.let { it * 2 >= firsts.size } ?: false
        }
        var crossing = 0
        var crossingCharacters = 0
        var edgeCrossingCharacters = 0
        var crossingOverRight = 0
        var best: Double? = null
        var bestCrossing = 0
        var bestCrossingInColumns = 0
        var bestCrossingCharacters = 0
        var fallback: Double? = null
        for (index in 0 until count) {
            crossing += blocked[index]
            crossingCharacters += blockedCharacters[index]
            edgeCrossingCharacters += blockedEdgeCharacters[index]
            crossingOverRight += blockedOverRight[index]
            val x = left + index * step
            if (x <= left + (right - left) * 0.25 || x >= left + (right - left) * 0.75) continue
            val leftCount = charactersByEnd[ends.countAtMost(x - band)]
            val rightCount = characters - charactersByStart[starts.countBelow(x)]
            val share = characters - edgeCrossingCharacters
            val crossingInColumns = crossing - crossingOverRight
            if (leftCount > share / 5 && rightCount > share / 5 && crossing <= rows.size / 5) {
                // Fewer crossing characters break a tie before the gap, so a page number centred under the gutter is the line
                // cut through rather than a lyric filled to its column's edge beside it.
                if (best == null || crossing < bestCrossing ||
                    crossing == bestCrossing && (
                        crossingInColumns < bestCrossingInColumns ||
                            crossingInColumns == bestCrossingInColumns &&
                            (crossingCharacters < bestCrossingCharacters || crossingCharacters == bestCrossingCharacters && gap(x) > gap(best))
                        )
                ) {
                    best = x
                    bestCrossing = crossing
                    bestCrossingInColumns = crossingInColumns
                    bestCrossingCharacters = crossingCharacters
                }
            } else if (crossing == 0 && rowEnds.countAtMost(x - band) >= 2 && rowStarts.size - rowStarts.countBelow(x) >= 2) {
                if (fallback == null || gap(x) > gap(fallback)) fallback = x
            }
        }
        return best?.takeIf { bestCrossing == 0 } ?: fallback?.takeIf(::isColumn) ?: best
    }

    /**
     * A run of a row's ink merged across gaps too narrow to hold the gutter band: it lies on one side of a gutter or crosses
     * it. Set at [y], it blocks the gutter positions [from] to [to], and [isEdge] for the top or the bottom row.
     */
    private data class Segment(
        val start: Double,
        val end: Double,
        val characters: Int,
        val y: Double,
        val from: Int,
        val to: Int,
        val isEdge: Boolean,
    )

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

    /** How many glyph comparisons the superscripts and subscripts of one set of lines may take. */
    private const val MAX_RAISED_WORK = 1L shl 24

    /** The largest share of its line's type a superscript or subscript is set in. */
    private const val RAISED_SIZE = 0.8

    /** How far, in ems of its line's type, a superscript's or subscript's baseline lies from its line's at most. */
    private const val RAISED_OFFSET = 0.5

    /** How far, in ems of its line's type, a superscript or subscript starts from the end of the text it follows at most. */
    private const val RAISED_ATTACHMENT = 0.15
}
