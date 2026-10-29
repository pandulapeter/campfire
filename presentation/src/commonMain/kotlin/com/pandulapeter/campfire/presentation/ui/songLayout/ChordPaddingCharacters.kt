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
import com.pandulapeter.campfire.chordpro.language.isStrongRtl

/**
 * The character that widens a piece of lyrics ending in [codePoint] to fit the chord over it: [PADDING] for every
 * script today.
 *
 * A joining script (Arabic, Syriac, N'Ko) would take its tatweel (U+0640) after one of its letters instead, since a space
 * after a joining letter ends the word's shape there, and padding inside a word would break its letters apart; a tatweel
 * stretches the joined word instead. Such a row of [JoiningPaddings] also needs its own padding width measured, and the
 * PDF's text layer to tell its tatweel padding from the text's.
 */
internal fun chordPaddingAfter(codePoint: Int): Char = JoiningPaddings.firstOrNull { (codePoints, _) -> codePoint in codePoints }?.second ?: PADDING

/**
 * The scripts padded with a character of their own rather than [PADDING], by the code points of the letters it may follow.
 * Arabic would be its first row (its letters, U+0620–U+064A, to U+0640), checked in Skia's FreeType path on the web and
 * Linux, where joining is untested.
 */
private val JoiningPaddings = emptyList<Pair<IntRange, Char>>()

/**
 * The mark that keeps the padding of [piece] on the side its chord hangs to, in a paragraph laid out in
 * [paragraphDirection], or null where the bidirectional algorithm puts it there by itself.
 *
 * A chord hangs from the first letter or digit of its piece in that character's own direction (a Latin word's chord to
 * the right of its first letter, even in a Hebrew line), but padding is neutral, and a neutral between two runs of
 * different directions takes the paragraph's: after a Latin word followed by Hebrew in a right-to-left line it would
 * open the gap on the word's left, under the next chord rather than its own. Closed in by marks of the piece's own
 * direction, it joins the piece's run instead. Digits are laid out left to right in either paragraph, so they count as
 * left to right here.
 */
internal fun bidiMarkFor(piece: String, paragraphDirection: WritingDirection): Char? {
    var index = 0
    while (index < piece.length) {
        val codePoint = piece.codePointAt(index)
        val direction = when {
            isStrongRtl(codePoint) -> WritingDirection.RIGHT_TO_LEFT
            codePoint < 0x10000 && codePoint.toChar().isLetterOrDigit() -> WritingDirection.LEFT_TO_RIGHT
            else -> null
        }
        if (direction != null) {
            return when {
                direction == paragraphDirection -> null
                direction == WritingDirection.RIGHT_TO_LEFT -> RIGHT_TO_LEFT_MARK
                else -> LEFT_TO_RIGHT_MARK
            }
        }
        index += if (codePoint >= 0x10000) 2 else 1
    }
    return null
}

/** The code point at [index], joining a surrogate pair into the one character it encodes. */
private fun String.codePointAt(index: Int): Int {
    val char = this[index]
    val next = getOrNull(index + 1)
    return if (char.isHighSurrogate() && next != null && next.isLowSurrogate()) {
        0x10000 + ((char.code - 0xD800) shl 10) + (next.code - 0xDC00)
    } else {
        char.code
    }
}

/** A strong left-to-right character that draws nothing. */
internal const val LEFT_TO_RIGHT_MARK = '‎'

/** A strong right-to-left character that draws nothing. */
internal const val RIGHT_TO_LEFT_MARK = '‏'
