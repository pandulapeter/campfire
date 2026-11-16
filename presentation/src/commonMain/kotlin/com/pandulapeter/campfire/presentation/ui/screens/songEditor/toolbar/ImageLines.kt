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

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.insert
import androidx.compose.ui.text.TextRange
import com.pandulapeter.campfire.chordpro.edit.ChordProImages

/** What [imageLinesInsertion] writes: [text] at [offset], the caret then at [caret]. */
internal data class ImageLines(val offset: Int, val text: String, val caret: Int)

/**
 * The `{image}` directive of each of [names], a line each, written where the caret is ([offset]: the end of the selection,
 * which is kept), on lines of their own, since ChordPro reads a directive only alone on its line: at the start of a line
 * they go above it, anywhere else below it, the line itself never split. The caret goes after the last one.
 */
internal fun imageLinesInsertion(text: String, offset: Int, names: List<String>): ImageLines {
    val lines = names.joinToString("\n", transform = ChordProImages::directive)
    val isLineStart = offset == 0 || text[offset - 1] == '\n'
    if (isLineStart && offset < text.length) return ImageLines(offset = offset, text = "$lines\n", caret = offset + lines.length)
    val lineEnd = text.indexOf('\n', offset).takeIf { it >= 0 } ?: text.length
    val inserted = if (isLineStart) lines else "\n$lines"
    return ImageLines(offset = lineEnd, text = inserted, caret = lineEnd + inserted.length)
}

/** Writes the directives of [names] at the caret through the field's own editing, so that it is one undo step, see [imageLinesInsertion]. */
internal fun TextFieldState.insertImageLines(names: List<String>) = edit {
    val insertion = imageLinesInsertion(originalText.toString(), maxOf(selection.start, selection.end), names)
    insert(insertion.offset, insertion.text)
    selection = TextRange(insertion.caret)
}
