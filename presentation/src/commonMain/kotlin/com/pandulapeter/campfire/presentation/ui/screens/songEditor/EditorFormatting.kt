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
 * ChordPro reads markup within a line only, so a selection of several lines formats each of them, keeping the
 * whitespace at either end outside the tags. Lines that are not lyrics are left alone: a directive, whose braces the
 * tags would land outside of, and a `#` comment, which is never shown. Those lines are taken off where every line that
 * is formatted already is, and otherwise only those that are not are formatted.
 */
internal fun formattingToggle(text: String, selectionStart: Int, selectionEnd: Int, tag: String): FormattingToggle {
    val opening = "<$tag>"
    val closing = "</$tag>"
    val start = minOf(selectionStart, selectionEnd)
    val end = maxOf(selectionStart, selectionEnd)
    val selected = text.substring(start, end)
    if ('\n' in selected) {
        val lines = selected.split('\n')
        val isFormatted = lines.filter { it.isFormattable() }.let { formattable ->
            formattable.isNotEmpty() && formattable.all { it.trim().isEnclosedIn(opening, closing) }
        }
        val replaced = lines.joinToString("\n") { line ->
            when {
                !line.isFormattable() -> line
                isFormatted -> line.withCore { it.substring(opening.length, it.length - closing.length) }
                line.trim().isEnclosedIn(opening, closing) -> line
                else -> line.withCore { opening + it + closing }
            }
        }
        return FormattingToggle(start, end, replaced, start, start + replaced.length)
    }
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

private fun String.isFormattable() = trim().let { it.isNotEmpty() && !it.startsWith('{') && !it.startsWith('#') }

private fun String.isEnclosedIn(opening: String, closing: String) =
    length >= opening.length + closing.length && startsWith(opening, ignoreCase = true) && endsWith(closing, ignoreCase = true)

/** The line with what lies between its leading and trailing whitespace changed by [change]. */
private fun String.withCore(change: (String) -> String): String {
    val coreStart = indexOfFirst { !it.isWhitespace() }
    val coreEnd = indexOfLast { !it.isWhitespace() } + 1
    return substring(0, coreStart) + change(substring(coreStart, coreEnd)) + substring(coreEnd)
}
