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
import com.pandulapeter.campfire.chordpro.edit.ChordProFormattingTags
import com.pandulapeter.campfire.chordpro.edit.ChordProMarkupLines
import com.pandulapeter.campfire.chordpro.model.ChordProLine.Lyrics.Formatting
import com.pandulapeter.campfire.chordpro.syntax.ChordProMarkup

/** Puts the tags of [style] around the selection, or takes them away where they already stand around it. */
internal fun TextFieldState.toggleFormatting(style: Formatting.Style) = edit {
    val toggle = formattingToggle(originalText.toString(), selection.min, selection.max, style)
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
 * The edit that formats the selection from [selectionStart] to [selectionEnd] of [text] in [style] (with `<b>`, `<i>`,
 * or `<u>`), or that takes the style off where it is already there, the way a word processor's Bold does: either tags
 * setting it stand right around the selection, or the selection starts and ends with them, in any spelling the parser
 * reads ([ChordProFormattingTags]). With nothing selected it writes an empty pair with the caret inside, or takes away
 * the empty pair the caret is in.
 *
 * Markup goes only where the parser reads it, [ChordProMarkupLines.markupRanges]: a lyric line, or a comment's value,
 * to which a selection on one line is clipped (a whole selected `{c: note}` formats `note`). A tab, grid, delegated, or
 * other directive line, a Campfire 3 heading, and a `#` comment are left alone. ChordPro reads markup within a line
 * only, so a selection of several lines formats each of them, keeping the whitespace at either end outside the tags.
 * The lines are taken off where every one that is formatted already is, and otherwise only those that are not are
 * formatted.
 */
internal fun formattingToggle(text: String, selectionStart: Int, selectionEnd: Int, style: Formatting.Style): FormattingToggle {
    val tag = ChordProMarkup.tagOf(style)
    val opening = "<$tag>"
    val closing = "</$tag>"
    val start = minOf(selectionStart, selectionEnd)
    val end = maxOf(selectionStart, selectionEnd)
    val selected = text.substring(start, end)
    val touched = ChordProMarkupLines.markupRanges(text).filterNotNull().filter { it.touches(text, start, end) }
    // A file with bare CR line endings has no \n between its lines, and the editor keeps such a file byte for byte.
    if ('\n' in selected || '\r' in selected || touched.size > 1) {
        val cores = touched.mapNotNull { range -> text.trimmedRange(maxOf(range.first, start), minOf(range.last + 1, end)) }
        val removals = cores.map { ChordProFormattingTags.inside(text, it.first, it.last + 1, style) }
        val isFormatted = cores.isNotEmpty() && removals.all { it != null }
        val replaced = StringBuilder(selected)
        // From the back, so that the offsets of the cores before each one stay valid.
        cores.indices.reversed().forEach { index ->
            val core = cores[index]
            val coreText = text.substring(core.first, core.last + 1)
            val changed = when {
                isFormatted -> removals[index]?.text ?: coreText
                removals[index] != null -> coreText
                else -> opening + coreText + closing
            }
            replaced.setRange(core.first - start, core.last + 1 - start, changed)
        }
        return FormattingToggle(start, end, replaced.toString(), start, start + replaced.length)
    }
    val range = touched.singleOrNull() ?: return FormattingToggle(start, end, selected, start, end)
    val clippedStart = maxOf(start, range.first)
    val clippedEnd = minOf(end, range.last + 1)
    // Only tags inside the range are the selection's: the `{c: ` before a comment's value is not.
    val removal = ChordProFormattingTags.around(text, clippedStart, clippedEnd, style, range)
        ?: ChordProFormattingTags.inside(text, clippedStart, clippedEnd, style)
    return if (removal != null) {
        FormattingToggle(removal.start, removal.end, removal.text, removal.contentStart, removal.contentStart + removal.contentLength)
    } else {
        val clipped = text.substring(clippedStart, clippedEnd)
        FormattingToggle(clippedStart, clippedEnd, opening + clipped + closing, clippedStart + opening.length, clippedStart + opening.length + clipped.length)
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
