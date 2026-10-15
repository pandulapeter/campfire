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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.ui.text.TextRange

/**
 * Formats the song as lines pasted into the editor's field arrive, see [formattedAfterPaste]. The paste and its
 * formatting are one edit, so Undo takes both back at once. An input transformation sees only what the user put into the
 * field - a paste, the keyboard, a drop - and never the editor's own edits of it (a transposition, a sheet's rewrite, the
 * respelling after a save), so none of those is ever reformatted; and only a paste of two lines or more counts, since a
 * keyboard commits a word, or a word it corrected, the way a one-line paste arrives. The lines are converted as written:
 * the field is in the reader's notation, and so was what they pasted.
 */
internal object PastedTextFormatting : InputTransformation {

    @OptIn(ExperimentalFoundationApi::class)
    override fun TextFieldBuffer.transformInput() {
        val insertions = (0 until changes.changeCount).map { index ->
            val range = changes.getRange(index)
            range.start to asCharSequence().subSequence(range.start, range.end)
        }
        val paste = pastedLinesOf(insertions) ?: return
        val (formatted, caret) = formattedAfterPaste(text = asCharSequence().toString(), paste = paste) ?: return
        replace(0, length, formatted)
        selection = TextRange(caret)
    }
}
