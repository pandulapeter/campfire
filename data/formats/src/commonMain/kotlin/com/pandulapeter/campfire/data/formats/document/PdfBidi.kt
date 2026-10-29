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

import com.pandulapeter.campfire.chordpro.language.BidiOrder
import com.pandulapeter.campfire.chordpro.language.WritingDirection
import com.pandulapeter.campfire.chordpro.language.isStrongRtl
import com.pandulapeter.campfire.data.model.importing.ExtractedDocument

/**
 * A line of glyphs, as most producers draw right-to-left text, put back into reading order. Browsers, office suites, and
 * generators with no bidi engine of their own draw a line's glyphs from the left, so a Hebrew or an Arabic word
 * comes out of the x order spelled backwards; only an `ActualText` (Campfire's own export) says the order itself, and
 * a line holding one is left as it is.
 */
internal object PdfBidi {
    /**
     * [spans], sorted from the left, in reading order, read in the direction [column] gives them: unchanged where nothing in them reads from the right, else one
     * span spelling the line's text with its glyphs as [ExtractedDocument.Span.pieces], each where it is drawn, so that
     * the converter can still tell which character a chord stands over. The line takes the style its letters share, as
     * one `ActualText` does: a chord sheet's line is set in one type.
     */
    fun readingOrder(spans: List<ExtractedDocument.Span>, column: Column): List<ExtractedDocument.Span> {
        if (spans.any { it.pieces.isNotEmpty() } || spans.none { span -> span.text.any { isStrongRtl(it.code) } }) return spans
        val clusters = clusters(spans)
        val levels = BidiOrder.levels(clusters.map { cluster -> cluster.joinToString("") { it.text } }, column.directionOf(spans))
        val order = BidiOrder.readingOrder(levels)
        if (order.withIndex().all { (index, value) -> index == value }) return spans
        val read = order.map { clusters[it] }
        // A bracket drawn in right-to-left text is the mirror image of the one it stands for, and producers disagree on
        // which one they name: a generator naming its glyphs by their shapes names the one drawn (the ")" left of a Hebrew
        // word, which opens it), Chrome the character it stands for. Whichever pairs the line's brackets up is the one this producer meant.
        val mirrored = order.mapIndexed { position, index ->
            if (levels[index] % 2 == 0) read[position]
            else read[position].map { piece -> piece.copy(text = piece.text.map(BidiOrder::mirrored).joinToString("")) }
        }
        val pieces = if (unpaired(mirrored.flatten()) <= unpaired(read.flatten())) mirrored.flatten() else read.flatten()
        val letters = pieces.filter { it.text.isNotBlank() }.ifEmpty { pieces }
        return listOf(
            ExtractedDocument.Span(
                text = pieces.joinToString("") { it.text },
                start = pieces.minOf { it.start },
                end = pieces.maxOf { it.end },
                size = letters.first().size,
                isBold = letters.all { it.isBold },
                isMonospace = letters.all { it.isMonospace },
                isRaised = letters.all { it.isRaised },
                isItalic = letters.all { it.isItalic },
                pieces = pieces,
            ),
        )
    }

    /**
     * The glyphs set into lines together ([PdfLineLayout.rows]): a column, whose edges are [left] and [right], and whose
     * letters mostly read in [direction].
     */
    class Column(val left: Double, val right: Double, val direction: WritingDirection) {
        /**
         * The direction a line of [spans] is read in, which a PDF does not say: a paragraph is aligned to the side it
         * starts from, so a line flush with one edge of the column and short of the other reads from that edge (an
         * English line in a Hebrew sheet, a Hebrew one in an English sheet), and one flush with both or neither, a full
         * or a centred line, reads as most of its column does. A line's own letters are no witness: a Hebrew paragraph
         * opening with a Latin title holds more Latin letters than Hebrew ones.
         */
        fun directionOf(spans: List<ExtractedDocument.Span>): WritingDirection {
            val em = spans.maxOf { it.size }
            val isFlushLeft = spans.first().start - left < em
            val isFlushRight = right - spans.last().end < em
            return when {
                isFlushRight && !isFlushLeft -> WritingDirection.RIGHT_TO_LEFT
                isFlushLeft && !isFlushRight -> WritingDirection.LEFT_TO_RIGHT
                else -> direction
            }
        }
    }

    /** How many of the brackets of the text [pieces] spell close nothing or are never closed. */
    private fun unpaired(pieces: List<ExtractedDocument.Span>): Int {
        var open = 0
        var unpaired = 0
        pieces.forEach { piece ->
            piece.text.forEach { character ->
                when (character) {
                    '(', '[', '{', '<' -> open++
                    ')', ']', '}', '>' -> if (open > 0) open-- else unpaired++
                }
            }
        }
        return unpaired + open
    }

    /**
     * The glyphs of a line grouped into what is read as one: a letter with the marks set over or under it (Hebrew points,
     * Arabic harakat), which a producer draws as glyphs of their own, on either side of the letter's start. A mark goes
     * with the letter it is drawn over, else with the one before it.
     */
    private fun clusters(spans: List<ExtractedDocument.Span>): List<List<ExtractedDocument.Span>> {
        val clusters = mutableListOf<MutableList<ExtractedDocument.Span>>()
        val leading = mutableListOf<ExtractedDocument.Span>()
        spans.forEachIndexed { index, span ->
            if (!span.isMark()) {
                clusters += mutableListOf(span).apply { addAll(leading) }
                leading.clear()
                return@forEachIndexed
            }
            val middle = (span.start + span.end) / 2
            val base = clusters.lastOrNull()?.first()
            val next = (index + 1 until spans.size).firstOrNull { !spans[it].isMark() }?.let(spans::get)
            val isOverNext = next != null && middle >= next.start && middle <= next.end && (base == null || middle > base.end)
            if (base == null || isOverNext) leading += span else clusters.last() += span
        }
        if (leading.isNotEmpty()) clusters += leading
        return clusters
    }

    private fun ExtractedDocument.Span.isMark() = text.isNotEmpty() &&
        text.all { it.category == CharCategory.NON_SPACING_MARK || it.category == CharCategory.ENCLOSING_MARK }
}
