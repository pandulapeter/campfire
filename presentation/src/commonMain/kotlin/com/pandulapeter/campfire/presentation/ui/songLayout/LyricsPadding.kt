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

import com.pandulapeter.campfire.chordpro.language.WritingDirection
import com.pandulapeter.campfire.chordpro.model.ChordProLine
import kotlin.math.ceil

/**
 * Returns a copy of the line where every piece of lyrics that sits under a chord is at least as wide as the chord
 * (plus [gap]), by appending padding ([chordPaddingAfter]: non-breaking spaces), each [paddingWidth] wide, to it. Chord
 * positions are updated to point into the padded lyrics.
 *
 * [paragraphDirection] is that of the paragraph the line is laid out in, where a bidirectional layout places its
 * characters: a piece read the other way (a Latin word in a Hebrew line) has its padding closed in by marks of its own
 * direction ([bidiMarkFor]), so the gap opens on the side its chord hangs to. Null where the caller draws every piece
 * left to right itself, as the PDF's rows do.
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
    paragraphDirection: WritingDirection?,
    marksPadding: Boolean = false,
): ChordProLine.Lyrics {
    val paddedLyrics = StringBuilder(text.substring(0, chords.first().position))
    // Where each piece of the text starts in the padded one, so that the formatting can be moved with it, and where in the
    // piece a direction mark went in, if one did.
    val pieceStarts = IntArray(chords.size)
    val markOffsets = IntArray(chords.size) { -1 }
    val paddedChords = chords.mapIndexed { index, chord ->
        val end = chords.getOrNull(index + 1)?.position ?: text.length
        val fragment = text.substring(chord.position, end)
        val paddedChord = chord.copy(position = paddedLyrics.length)
        pieceStarts[index] = paddedLyrics.length
        val missingWidth = chordWidths[index] + gap - measureWidth(fragment)
        val paddingCount = if (missingWidth > 0 && paddingWidth > 0) ceil(missingWidth / paddingWidth).toInt() else 0
        val isBeforeMark = chords.getOrNull(index + 1)?.isMark == true
        if (isBeforeMark || paddingCount > 0) {
            val innerStart = fragment.indexOfFirst { !it.isWhitespace() }.let { if (it < 0) fragment.length else it }
            val innerEnd = fragment.indexOfLast { !it.isWhitespace() } + 1
            // The mark goes right after the piece's last letter rather than after its trailing spaces: a space between a
            // number and the mark would still be neutral between two directions, and take the paragraph's.
            val bidiMark = if (paddingCount > 0 && paragraphDirection != null) bidiMarkFor(fragment, paragraphDirection) else null
            fragment.forEachIndexed { characterIndex, character ->
                if (characterIndex == innerEnd && bidiMark != null) paddedLyrics.append(bidiMark)
                // Only the spaces at either end of a piece are made non-breaking, except before a mark, which no space of
                // the piece may be sent off from.
                val isEdgeSpace = isBeforeMark || characterIndex < innerStart || characterIndex >= innerEnd
                paddedLyrics.append(if (character.isWhitespace() && isEdgeSpace) PADDING else character)
            }
            if (innerEnd == fragment.length && bidiMark != null) paddedLyrics.append(bidiMark)
            if (bidiMark != null) markOffsets[index] = innerEnd
            if (paddingCount > 0) {
                if (marksPadding) paddedLyrics.append(PADDING_MARK)
                val paddingCharacter = chordPaddingAfter(fragment.lastOrNull()?.code ?: 0)
                repeat(paddingCount) { paddedLyrics.append(paddingCharacter) }
            }
            if (bidiMark != null) paddedLyrics.append(bidiMark)
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
            range.copy(
                start = paddedOffset(range.start, pieceStarts, markOffsets, isEnd = false),
                end = paddedOffset(range.end, pieceStarts, markOffsets, isEnd = true),
            )
        },
    )
}

/**
 * Where [offset] of the text stands in the padded one, whose pieces start at [pieceStarts]. An offset at the end of a
 * piece is both its end and the next one's start: the end of a range stops before the padding, which is not part of the
 * text it formats, and the start of one comes after it. A direction mark the piece got at [markOffsets] moves what follows
 * it by one, and an end at the mark stops before it.
 */
private fun ChordProLine.Lyrics.paddedOffset(offset: Int, pieceStarts: IntArray, markOffsets: IntArray, isEnd: Boolean): Int {
    var piece = -1
    for (index in chords.indices) {
        val position = chords[index].position
        if (if (isEnd) position < offset else position <= offset) piece = index else break
    }
    if (piece < 0) return offset
    val pieceOffset = offset - chords[piece].position
    val markOffset = markOffsets[piece]
    val isAfterMark = markOffset >= 0 && (pieceOffset > markOffset || pieceOffset == markOffset && !isEnd)
    return pieceStarts[piece] + pieceOffset + if (isAfterMark) 1 else 0
}

/**
 * A zero-width space, which is where a line padded to fit its chords may wrap: default-ignorable, so no font draws
 * anything for it, and a break opportunity after it to every line breaker (class ZW).
 */
private const val BREAK_OPPORTUNITY = '\u200B'

internal const val PADDING = '\u00A0' // Non-breaking space, so that the padding never gets trimmed or wrapped.

/** A word joiner: default-ignorable, so nothing is drawn for it, and no break opportunity either side of it. */
internal const val PADDING_MARK = '\u2060'
