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
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import com.pandulapeter.campfire.chordpro.edit.ChordProLineDirections
import com.pandulapeter.campfire.chordpro.language.WritingDirection

/**
 * Moves the caret with Left and Right by the direction of the line it is on rather than the song's, [songDirection].
 *
 * The field lays every line out in the song's direction (see [ChordProTextField]), and its own arrow keys go by that
 * paragraph direction: in a right-to-left song a directive, a `#` line, or a tab row is one left-to-right run, on which
 * Left would move the caret one character later, visually to the right, and the mirror holds for a Hebrew lyric line in
 * a left-to-right song. Such a line's keys are answered here (`ChordProLineDirections`), one grapheme a press, Shift
 * moving only the selection's end; a line read the song's way is left to the field. Word and line steps (with Ctrl,
 * Alt, or Cmd held) stay the platform's, since which modifier means which differs between Apple's platforms and the
 * rest. The key-up of a key answered here is taken too, so that nothing under this sees half a press.
 */
internal fun Modifier.editorArrowKeys(textFieldState: TextFieldState, songDirection: () -> WritingDirection) = onPreviewKeyEvent { keyEvent ->
    val isForwardOnLeftToRight = when (keyEvent.key) {
        Key.DirectionLeft -> false
        Key.DirectionRight -> true
        else -> return@onPreviewKeyEvent false
    }
    if (keyEvent.isCtrlPressed || keyEvent.isAltPressed || keyEvent.isMetaPressed) return@onPreviewKeyEvent false
    val text = textFieldState.text.toString()
    val selection = textFieldState.selection
    val lineDirection = ChordProLineDirections.directionOfLineAt(text, selection.end)
    if (lineDirection == songDirection()) return@onPreviewKeyEvent false
    if (keyEvent.type == KeyEventType.KeyDown) {
        val isForward = isForwardOnLeftToRight == (lineDirection == WritingDirection.LEFT_TO_RIGHT)
        val newSelection = arrowKeySelection(text = text, selection = selection, isForward = isForward, isExtending = keyEvent.isShiftPressed)
        textFieldState.edit { this.selection = newSelection }
    }
    true
}

/**
 * Where an arrow key moving [isForward] in the text takes [selection]: a grapheme further from its end, keeping its
 * start as the anchor while [isExtending], or, a selection being collapsed, to its end in that direction.
 */
internal fun arrowKeySelection(text: CharSequence, selection: TextRange, isForward: Boolean, isExtending: Boolean): TextRange = when {
    isExtending -> TextRange(selection.start, if (isForward) nextGraphemeOffset(text, selection.end) else previousGraphemeOffset(text, selection.end))
    !selection.collapsed -> TextRange(if (isForward) selection.max else selection.min)
    else -> TextRange(if (isForward) nextGraphemeOffset(text, selection.end) else previousGraphemeOffset(text, selection.end))
}

/** The offset one grapheme after [offset]: a surrogate pair and a line break of two characters whole, marks with their letter. */
internal fun nextGraphemeOffset(text: CharSequence, offset: Int): Int {
    if (offset >= text.length) return text.length
    var next = offset + 1
    if (next < text.length && (text[offset].isHighSurrogate() && text[next].isLowSurrogate() || text[offset] == '\r' && text[next] == '\n')) next++
    while (next < text.length && text[next].isCombiningMark()) next++
    return next
}

/** The offset one grapheme before [offset], by the rule of [nextGraphemeOffset]. */
internal fun previousGraphemeOffset(text: CharSequence, offset: Int): Int {
    if (offset <= 0) return 0
    var previous = offset.coerceAtMost(text.length) - 1
    while (previous > 0 && text[previous].isCombiningMark()) previous--
    if (previous > 0 && (text[previous].isLowSurrogate() && text[previous - 1].isHighSurrogate() || text[previous] == '\n' && text[previous - 1] == '\r')) previous--
    return previous
}

/** Whether this is a mark drawn on the letter before it (a Hebrew point), which a caret never stops before. */
private fun Char.isCombiningMark() = category == CharCategory.NON_SPACING_MARK ||
    category == CharCategory.ENCLOSING_MARK ||
    category == CharCategory.COMBINING_SPACING_MARK
