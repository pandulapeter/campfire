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
import com.pandulapeter.campfire.presentation.ui.rendering.SongRenderer
import com.pandulapeter.campfire.presentation.ui.state.editor.EditorSession
import kotlinx.coroutines.flow.StateFlow

/**
 * Puts the file back into the field once the user has confirmed that is what they want. It goes through the field's
 * own editing rather than through a new [TextFieldState], so that a revert is one more step of the undo history and
 * not the end of it.
 */
@Composable
internal fun RevertOnRequest(
    editorSession: EditorSession,
    songTexts: StateFlow<Map<String, String>>,
    songRenderer: SongRenderer,
    fileName: String,
    textFieldState: TextFieldState,
    summaryCache: ChordProSummaryCache,
) {
    val notation = editorSession.editorNotation
    LaunchedEffect(textFieldState, fileName) {
        editorSession.editorRevertRequests.collect {
            songTexts.value[fileName]?.let { text ->
                summaryCache.clear()
                textFieldState.replaceAll(songRenderer.editorTextOf(text, notation), isSelectionMapped = false)
            }
        }
    }
}
