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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.pandulapeter.campfire.chordpro.ChordProSummaryCache
import com.pandulapeter.campfire.presentation.ui.screens.songEditor.toolbar.insertImageLines
import com.pandulapeter.campfire.presentation.ui.state.editor.EditorSession
import kotlinx.coroutines.flow.filter

/**
 * Applies what the metadata dialogs opened from the editor's menu change, and the pictures its Image button kept
 * (`EditorImages.insertions`, written at the caret), to the text being typed rather than to the file. It goes through the
 * field's own editing, as a revert does, so that the change is one more step of the undo history and is written by Save
 * together with everything else typed.
 */
@Composable
internal fun EditOnRequest(
    editorSession: EditorSession,
    fileName: String,
    textFieldState: TextFieldState,
    summaryCache: ChordProSummaryCache,
) {
    LaunchedEffect(textFieldState, fileName) {
        editorSession.editorTextEdits.filter { it.fileName == fileName }.collect { request ->
            val text = textFieldState.text.toString()
            val edited = request.edit(text)
            if (edited != text) {
                summaryCache.clear()
                textFieldState.replaceAll(edited, isSelectionMapped = true)
            }
        }
    }
    LaunchedEffect(textFieldState, fileName) {
        // Taken whichever editor it was for, so that one left behind by an editor that is gone is dropped, not kept for the next.
        editorSession.images.insertions.collect { insertion ->
            if (insertion.fileName == fileName) {
                summaryCache.clear()
                textFieldState.insertImageLines(insertion.names)
            }
        }
    }
}
