/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.print

import com.pandulapeter.campfire.chordpro.model.ChordProLine.Lyrics.Formatting

/** A piece of a printed lyric row set in one style, starting at [offset] into the row's text. */
internal data class PrintLyricPiece(
    val text: String,
    val offset: Int,
    val style: PrintStyle,
)

/**
 * The pieces of [fragment], the text from [start] on, each in [base] with the bold and italic of the [formatting] that
 * covers it, which is what the width of a styled prefix of the row is measured in. A fragment no formatting reaches is one
 * piece; underlining changes no width.
 */
internal fun styledPieces(formatting: List<Formatting>, start: Int, fragment: String, base: PrintStyle): List<PrintLyricPiece> {
    val end = start + fragment.length
    val reaching = formatting.filter { it.start < end && it.end > start }
    if (reaching.isEmpty()) return listOf(PrintLyricPiece(fragment, 0, base))
    val boundaries = (reaching.flatMap { listOf(it.start, it.end) }.filter { it in start + 1 until end } + start + end).distinct().sorted()
    return boundaries.zipWithNext { pieceStart, pieceEnd ->
        fun has(style: Formatting.Style) = reaching.any { it.style == style && it.start <= pieceStart && it.end >= pieceEnd }
        PrintLyricPiece(
            text = fragment.substring(pieceStart - start, pieceEnd - start),
            offset = pieceStart - start,
            style = base.copy(
                bold = base.bold || has(Formatting.Style.BOLD),
                italic = base.italic || has(Formatting.Style.ITALIC),
            ),
        )
    }
}

/** The formatting of [this] that reaches [fragment], the text from [start] on, as offsets into the fragment. */
internal fun List<Formatting>.clippedTo(start: Int, fragment: String): List<Formatting> {
    val end = start + fragment.length
    return mapNotNull { range ->
        val from = maxOf(range.start, start)
        val to = minOf(range.end, end)
        if (from < to) Formatting(from - start, to - start, range.style) else null
    }
}

/**
 * What a text with [this] formatting is wrapped measured in: bold where any of it is, so that a bold word never reaches
 * past the column, since the wrapping cannot tell which of the substrings it tries are bold.
 */
internal fun List<Formatting>.wrappingStyle(base: PrintStyle) = if (any { it.style == Formatting.Style.BOLD }) base.copy(bold = true) else base

/** The width of the first [length] characters of the row the pieces make up. */
internal fun List<PrintLyricPiece>.widthUntil(length: Int, measure: (String, PrintStyle) -> Float) = sumOf { piece ->
    val taken = (length - piece.offset).coerceIn(0, piece.text.length)
    if (taken == 0) 0.0 else measure(piece.text.take(taken), piece.style).toDouble()
}.toFloat()
