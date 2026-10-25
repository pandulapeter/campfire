/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songEditor

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.text.TextRange
import com.pandulapeter.campfire.chordpro.edit.ChordProLiteralLines
import com.pandulapeter.campfire.chordpro.edit.ChordProMarkupLines
import com.pandulapeter.campfire.chordpro.model.ChordProLine.Lyrics.Formatting
import com.pandulapeter.campfire.chordpro.syntax.ChordProMarkup

/** Puts the tags of [style] around the selection, or takes them away where they already stand around it. */
internal fun TextFieldState.toggleFormatting(style: Formatting.Style) = edit {
    val toggle = formattingToggle(originalText.toString(), selection.min, selection.max, ChordProMarkup.tagOf(style))
    replace(toggle.start, toggle.end, toggle.text)
    selection = TextRange(toggle.selectionStart, toggle.selectionEnd)
}

/** What [formattingToggle] writes: [text] in place of [start] to [end], then the selection. */
internal data class FormattingToggle(
    val start: Int,
    val end: Int,
    val text: String,
    val selectionStart: Int,
    val selectionEnd: Int,
)

/**
 * The edit that formats the selection from [selectionStart] to [selectionEnd] of [text] with [tag] (`b`), or that takes
 * the formatting off where it is already there, the way a word processor's Bold does: either the tags stand right
 * around the selection, or the selection starts and ends with them. With nothing selected it writes an empty pair with
 * the caret inside, or takes away the empty pair the caret is in.
 *
 * Markup goes only where the parser reads it, [ChordProMarkupLines.markupRanges]: a lyric line, or a comment's value.
 * ChordPro reads markup within a line only, so a selection of several lines formats each of them, keeping the
 * whitespace at either end outside the tags. A tab, grid, delegated, or other directive line, a Campfire 3 heading,
 * and a `#` comment are left alone. The lines are taken off where every one that is formatted already is, and otherwise
 * only those that are not are formatted.
 */
internal fun formattingToggle(text: String, selectionStart: Int, selectionEnd: Int, tag: String): FormattingToggle {
    val opening = "<$tag>"
    val closing = "</$tag>"
    val start = minOf(selectionStart, selectionEnd)
    val end = maxOf(selectionStart, selectionEnd)
    val selected = text.substring(start, end)
    val touched = ChordProMarkupLines.markupRanges(text).filterNotNull().filter { it.touches(text, start, end) }
    // A file with bare CR line endings has no \n between its lines, and the editor keeps such a file byte for byte.
    if ('\n' in selected || '\r' in selected || touched.size > 1) {
        val cores = touched.mapNotNull { range -> text.trimmedRange(maxOf(range.first, start), minOf(range.last + 1, end)) }
        val isFormatted = cores.isNotEmpty() && cores.all { text.substring(it.first, it.last + 1).isEnclosedIn(opening, closing) }
        val replaced = StringBuilder(selected)
        // From the back, so that the offsets of the cores before each one stay valid.
        cores.asReversed().forEach { core ->
            val coreText = text.substring(core.first, core.last + 1)
            val changed = when {
                isFormatted -> coreText.substring(opening.length, coreText.length - closing.length)
                coreText.isEnclosedIn(opening, closing) -> coreText
                else -> opening + coreText + closing
            }
            replaced.setRange(core.first - start, core.last + 1 - start, changed)
        }
        return FormattingToggle(start, end, replaced.toString(), start, start + replaced.length)
    }
    if (ChordProLiteralLines.isLiteralAt(text, start)) return FormattingToggle(start, end, selected, start, end)
    val isAroundSelection = start >= opening.length && text.regionMatches(start - opening.length, opening, 0, opening.length, ignoreCase = true) &&
        text.regionMatches(end, closing, 0, closing.length, ignoreCase = true)
    return when {
        isAroundSelection -> FormattingToggle(start - opening.length, end + closing.length, selected, start - opening.length, end - opening.length)
        selected.isEnclosedIn(opening, closing) -> {
            val inner = selected.substring(opening.length, selected.length - closing.length)
            FormattingToggle(start, end, inner, start, start + inner.length)
        }
        else -> FormattingToggle(start, end, opening + selected + closing, start + opening.length, start + opening.length + selected.length)
    }
}

/**
 * Whether markup read in this range of [text] is touched by the selection from [start] to [end]: the part of it
 * selected is more than whitespace, or, with a caret, the caret stands in it or right after it (at a line's end, or in
 * an empty comment value).
 */
private fun IntRange.touches(text: String, start: Int, end: Int) =
    if (start == end) start in first..last + 1 else text.trimmedRange(maxOf(first, start), minOf(last + 1, end)) != null

/** The part of [this] from [from] to [until] without its leading and trailing whitespace, or null where nothing is left. */
private fun String.trimmedRange(from: Int, until: Int): IntRange? {
    var trimmedFrom = from
    var trimmedUntil = until
    while (trimmedFrom < trimmedUntil && this[trimmedFrom].isWhitespace()) trimmedFrom++
    while (trimmedUntil > trimmedFrom && this[trimmedUntil - 1].isWhitespace()) trimmedUntil--
    return if (trimmedFrom < trimmedUntil) trimmedFrom until trimmedUntil else null
}

private fun String.isEnclosedIn(opening: String, closing: String) =
    length >= opening.length + closing.length && startsWith(opening, ignoreCase = true) && endsWith(closing, ignoreCase = true)
