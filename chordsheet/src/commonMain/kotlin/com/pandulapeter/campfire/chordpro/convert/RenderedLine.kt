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

/**
 * [isFromLabel] is the chord row a heading's label was followed by on its line, which no lyric is written under;
 * [marks] what Campfire's export says of the line, null for a line of any other sheet.
 */
internal data class Rendered(
    val text: String,
    val positions: DoubleArray,
    val source: ChordSheet.Line,
    val isFromLabel: Boolean = false,
    val isMarginLabel: Boolean = false,
    val isHeaderPart: Boolean = false,
    /** Where the chords over a row holding right-to-left text in reading order hang from. */
    val bidi: ChordSheetBidiAnchors? = null,
    val marks: CampfireMarks? = null,
) {
    /** What goes between the row this one continues and its text. */
    val joint get() = if (marks?.isHardCut == true) "" else " "
}

internal data class Token(val text: String, val index: Int)

/**
 * A row of chords, and the playing note it may end in, `G   D   (let ring)`. The note is kept apart from the
 * tokens, which every rule that judges the row reads, and is written after the line rather than set as a chord.
 * [notes] are the ones printed before or between the chords, `(x) C D` or `G D (to v:) Em` (a songbook's `[*…]`
 * annotations), each one word to four in parentheses, set where they stand like the chords.
 */
internal class ChordRow(val tokens: List<Token>, val annotation: String?, val notes: List<Token>)
