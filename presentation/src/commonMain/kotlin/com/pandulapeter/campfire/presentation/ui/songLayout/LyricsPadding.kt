/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songLayout

import com.pandulapeter.campfire.chordpro.model.ChordProLine
import kotlin.math.ceil

/**
 * Returns a copy of the line where every piece of lyrics that sits under a chord is at least as wide as the chord
 * (plus [gap]), by appending non-breaking spaces, each [paddingWidth] wide, to it. Chord positions are updated to point
 * into the padded lyrics.
 *
 * The padded line may only wrap between two chords, and only where the original text has a word boundary: the
 * whitespace at either end of a padded piece is made non-breaking too, so that a chord, the space it sits on and the
 * padding that makes room for it never end up on two rows, and a [BREAK_OPPORTUNITY] follows every piece that ends at a
 * word boundary. Without it a chord-only line would be one unbreakable run the layout can only break at an arbitrary
 * character, leaving the chord at the end of a row with no room for it; with it every chord starts whatever row it is
 * sent to. The inner spaces of a piece stay ordinary, so a long piece of lyrics under one chord still wraps between its
 * words. A mark ([ChordProLine.Lyrics.Chord.isMark]) is never a row's start: an `x4` or a bar line sent off on its own
 * would no longer say what it repeats or ends, so the piece before one is never broken.
 *
 * Where [marksPadding], every run of padding is opened by a [PADDING_MARK], which tells it from the spaces of the text
 * made non-breaking: the PDF writes those into its selectable text as spaces and the padding as what it is, so that the
 * import reads back the words as they were written.
 */
internal fun ChordProLine.Lyrics.padLyricsToFitChords(
    chordWidths: List<Float>,
    gap: Float,
    paddingWidth: Float,
    measureWidth: (String) -> Float,
    marksPadding: Boolean = false,
): ChordProLine.Lyrics {
    val paddedLyrics = StringBuilder(text.substring(0, chords.first().position))
    fun pad(count: Int) {
        if (marksPadding && count > 0) paddedLyrics.append(PADDING_MARK)
        repeat(count) { paddedLyrics.append(PADDING) }
    }
    // Where each piece of the text starts in the padded one, so that the formatting can be moved with it.
    val pieceStarts = IntArray(chords.size)
    val paddedChords = chords.mapIndexed { index, chord ->
        val end = chords.getOrNull(index + 1)?.position ?: text.length
        val fragment = text.substring(chord.position, end)
        val paddedChord = chord.copy(position = paddedLyrics.length)
        pieceStarts[index] = paddedLyrics.length
        val missingWidth = chordWidths[index] + gap - measureWidth(fragment)
        val isBeforeMark = chords.getOrNull(index + 1)?.isMark == true
        if (isBeforeMark) {
            fragment.forEach { paddedLyrics.append(if (it.isWhitespace()) PADDING else it) }
            if (missingWidth > 0 && paddingWidth > 0) pad(ceil(missingWidth / paddingWidth).toInt())
        } else if (missingWidth > 0 && paddingWidth > 0) {
            val innerStart = fragment.indexOfFirst { !it.isWhitespace() }.let { if (it < 0) fragment.length else it }
            val innerEnd = fragment.indexOfLast { !it.isWhitespace() } + 1
            repeat(innerStart) { paddedLyrics.append(PADDING) }
            if (innerEnd > innerStart) paddedLyrics.append(fragment, innerStart, innerEnd)
            repeat(fragment.length - maxOf(innerStart, innerEnd)) { paddedLyrics.append(PADDING) }
            pad(ceil(missingWidth / paddingWidth).toInt())
        } else {
            paddedLyrics.append(fragment)
        }
        val isWordBoundary = end == 0 || text.getOrNull(end - 1)?.isWhitespace() == true || text.getOrNull(end)?.isWhitespace() == true
        if (index < chords.lastIndex && isWordBoundary && !isBeforeMark) paddedLyrics.append(BREAK_OPPORTUNITY)
        paddedChord
    }
    return ChordProLine.Lyrics(
        text = paddedLyrics.toString(),
        chords = paddedChords,
        formatting = formatting.map { range ->
            range.copy(start = paddedOffset(range.start, pieceStarts, isEnd = false), end = paddedOffset(range.end, pieceStarts, isEnd = true))
        },
    )
}

/**
 * Where [offset] of the text stands in the padded one, whose pieces start at [pieceStarts]. An offset at the end of a
 * piece is both its end and the next one's start: the end of a range stops before the padding, which is not part of the
 * text it formats, and the start of one comes after it.
 */
private fun ChordProLine.Lyrics.paddedOffset(offset: Int, pieceStarts: IntArray, isEnd: Boolean): Int {
    var piece = -1
    for (index in chords.indices) {
        val position = chords[index].position
        if (if (isEnd) position < offset else position <= offset) piece = index else break
    }
    return if (piece < 0) offset else pieceStarts[piece] + offset - chords[piece].position
}

/**
 * A zero-width space, which is where a line padded to fit its chords may wrap: default-ignorable, so no font draws
 * anything for it, and a break opportunity after it to every line breaker (class ZW).
 */
private const val BREAK_OPPORTUNITY = '\u200B'

internal const val PADDING = '\u00A0' // Non-breaking space, so that the padding never gets trimmed or wrapped.

/** A word joiner: default-ignorable, so nothing is drawn for it, and no break opportunity either side of it. */
internal const val PADDING_MARK = '\u2060'
