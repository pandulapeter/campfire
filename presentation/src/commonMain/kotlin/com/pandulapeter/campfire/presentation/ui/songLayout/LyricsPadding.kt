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
 * words.
 */
internal fun ChordProLine.Lyrics.padLyricsToFitChords(
    chordWidths: List<Float>,
    gap: Float,
    paddingWidth: Float,
    measureWidth: (String) -> Float,
): ChordProLine.Lyrics {
    val paddedLyrics = StringBuilder(text.substring(0, chords.first().position))
    val paddedChords = chords.mapIndexed { index, chord ->
        val end = chords.getOrNull(index + 1)?.position ?: text.length
        val fragment = text.substring(chord.position, end)
        val paddedChord = chord.copy(position = paddedLyrics.length)
        val missingWidth = chordWidths[index] + gap - measureWidth(fragment)
        if (missingWidth > 0 && paddingWidth > 0) {
            val innerStart = fragment.indexOfFirst { !it.isWhitespace() }.let { if (it < 0) fragment.length else it }
            val innerEnd = fragment.indexOfLast { !it.isWhitespace() } + 1
            repeat(innerStart) { paddedLyrics.append(PADDING) }
            if (innerEnd > innerStart) paddedLyrics.append(fragment, innerStart, innerEnd)
            repeat(fragment.length - maxOf(innerStart, innerEnd)) { paddedLyrics.append(PADDING) }
            repeat(ceil(missingWidth / paddingWidth).toInt()) { paddedLyrics.append(PADDING) }
        } else {
            paddedLyrics.append(fragment)
        }
        val isWordBoundary = end == 0 || text.getOrNull(end - 1)?.isWhitespace() == true || text.getOrNull(end)?.isWhitespace() == true
        if (index < chords.lastIndex && isWordBoundary) paddedLyrics.append(BREAK_OPPORTUNITY)
        paddedChord
    }
    return ChordProLine.Lyrics(text = paddedLyrics.toString(), chords = paddedChords)
}

/**
 * A zero-width space, which is where a line padded to fit its chords may wrap: default-ignorable, so no font draws
 * anything for it, and a break opportunity after it to every line breaker (class ZW).
 */
private const val BREAK_OPPORTUNITY = '\u200B'

internal const val PADDING = '\u00A0' // Non-breaking space, so that the padding never gets trimmed or wrapped.
