/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songEditor.toolbar

import androidx.compose.ui.text.TextRange

/**
 * Whether a write from the toolbar, which took [textBefore] with [selectionBefore] to [textAfter] with [selectionAfter],
 * left a value to type: something it selected to be typed over (a header line's value, a chord shape's frets), or the
 * caret inside the braces of a directive or the brackets of a chord, where what the button wrote is still missing its
 * value. A section or a tab opened around the caret, a chorus recalled, and a selection only wrapped leave none: what
 * comes after them is as often another button as typing.
 */
internal fun leavesValueToType(
    textBefore: String,
    selectionBefore: TextRange,
    textAfter: String,
    selectionAfter: TextRange,
): Boolean = if (selectionAfter.collapsed) {
    val caret = selectionAfter.start.coerceIn(0, textAfter.length)
    val lineBeforeCaret = textAfter.substring(textAfter.lastIndexOf('\n', caret - 1) + 1, caret)
    lineBeforeCaret.lastIndexOfAny(charArrayOf('{', '[')) > lineBeforeCaret.lastIndexOfAny(charArrayOf('}', ']'))
} else {
    textAfter.substring(selectionAfter.min, selectionAfter.max) != textBefore.substring(selectionBefore.min, selectionBefore.max)
}
