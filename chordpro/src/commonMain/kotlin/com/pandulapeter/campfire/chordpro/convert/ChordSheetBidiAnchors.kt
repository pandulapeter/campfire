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

import com.pandulapeter.campfire.chordpro.language.isStrongRtl
import kotlin.math.abs

/**
 * Where each character of a row that holds right-to-left text is drawn. Campfire's export writes such a row as one text
 * in its reading order, and the import puts another producer's glyphs, or a plain text's columns, into that order too
 * ([ChordSheet.Span.pieces] being its glyphs), which runs leftwards and, around a Latin word, rightwards again, so its
 * characters' positions no longer grow with their index: a chord is set on the character it hangs from as the export
 * hangs it, a right-to-left letter's chord ending at the letter's right edge and a left-to-right one's starting at its
 * left.
 */
internal class ChordSheetBidiAnchors private constructor(
    private val left: DoubleArray,
    private val right: DoubleArray,
    private val classes: IntArray,
    private val rowStart: Double,
) {
    /**
     * The character a chord whose left edge is at [chordStart] and whose right edge is at [chordEnd] hangs from, [length]
     * (the row's end) for a chord after its last one, which in a right-to-left row ends where the row starts on the left;
     * null where no character is within [tolerance]. A chord's right edge is allowed a point more: the export sets a chord
     * hanging leftwards by its width in whole points, so its glyphs end up to a point short of the edge it hangs from.
     */
    fun indexOf(chordStart: Double, chordEnd: Double, length: Int, tolerance: Double): Int? {
        var best: Int? = null
        var bestDistance = Double.MAX_VALUE
        fun consider(index: Int, distance: Double) {
            if (distance < bestDistance) {
                best = index
                bestDistance = distance
            }
        }
        for (index in 0 until minOf(length, classes.size)) {
            if (left[index].isNaN() || classes[index] == MARK) continue
            if (classes[index] != LEFT_TO_RIGHT) abs(right[index] - chordEnd).takeIf { it <= tolerance + ROUNDING }?.let { consider(index, it) }
            if (classes[index] != RIGHT_TO_LEFT) abs(chordStart - left[index]).takeIf { it <= tolerance }?.let { consider(index, it) }
        }
        abs(rowStart - chordEnd).takeIf { it <= tolerance + ROUNDING }?.let { consider(length, it) }
        return best
    }

    /** The characters of a row, a span at a time, in the order the converter renders them. */
    class Builder {
        private val left = mutableListOf<Double>()
        private val right = mutableListOf<Double>()
        private val classes = mutableListOf<Int>()
        private var rowStart = Double.MAX_VALUE
        private var isLost = false

        /** A space the converter puts between two spans, which no chord hangs from. */
        fun gap() {
            left += Double.NaN
            right += Double.NaN
            classes += NEUTRAL
        }

        /**
         * The characters of [span] as the converter renders them: [keep] turns each into what it renders, nothing for
         * padding, and [rendered] is the text that came of them, which no longer maps onto them where it is not as long. A
         * span with no pieces is placed as the converter places it, [width] a character.
         */
        fun span(span: ChordSheet.Span, keep: (Char) -> String, rendered: String, width: Double) {
            var count = 0
            fun add(character: Char, start: Double, end: Double) = repeat(keep(character).length) {
                left += start
                right += end
                classes += classOf(character)
                count++
            }
            if (span.pieces.isEmpty()) {
                span.text.forEach { character -> (span.start + count * width).let { add(character, it, it + width) } }
            } else {
                span.pieces.forEach { piece -> piece.text.forEach { add(it, piece.start, piece.end) } }
                if (span.text.any { isStrongRtl(it.code) }) rowStart = minOf(rowStart, span.start)
            }
            if (count != rendered.length) isLost = true
        }

        /** The row's anchors, or null for a row with no right-to-left text written in its order, read by its positions. */
        fun build() = if (rowStart != Double.MAX_VALUE && !isLost) {
            ChordSheetBidiAnchors(left.toDoubleArray(), right.toDoubleArray(), classes.toIntArray(), rowStart)
        } else {
            null
        }
    }

    internal companion object {
        /**
         * Where each character of [rendered], what [span] renders as, starts where the span is written in reading order
         * over its glyphs ([ChordSheet.Span.pieces]), so that a chord row or a lyric so written is placed by them; null
         * where the span has no pieces or they no longer map onto what it renders.
         */
        fun starts(span: ChordSheet.Span, rendered: String): DoubleArray? {
            if (span.pieces.isEmpty() || span.pieces.sumOf { it.text.length } != rendered.length) return null
            val starts = DoubleArray(rendered.length)
            var index = 0
            span.pieces.forEach { piece ->
                val width = (piece.end - piece.start) / piece.text.length.coerceAtLeast(1)
                repeat(piece.text.length) { starts[index++] = piece.start + it * width }
            }
            return starts
        }

        /**
         * Which edge a chord on [character] may hang from: a right-to-left letter's right, a left-to-right one's left
         * (a digit's too), and either for a space, which takes the direction of what is around it. A combining mark (a
         * Hebrew point, an Arabic vowel) is none: it belongs to the letter before it, and a PDF often draws it within the
         * next letter's glyph, sharing that letter's edges, where a chord taking it would split a letter from its marks.
         */
        private fun classOf(character: Char) = when {
            character.category.let { it == CharCategory.NON_SPACING_MARK || it == CharCategory.ENCLOSING_MARK || it == CharCategory.COMBINING_SPACING_MARK } -> MARK
            isStrongRtl(character.code) -> RIGHT_TO_LEFT
            character.isLetterOrDigit() -> LEFT_TO_RIGHT
            else -> NEUTRAL
        }

        /** How far short of its edge the export's rounded width leaves a chord hanging leftwards, at most. */
        private const val ROUNDING = 1.0

        private const val NEUTRAL = 0
        private const val LEFT_TO_RIGHT = 1
        private const val RIGHT_TO_LEFT = 2
        private const val MARK = 3
    }
}
