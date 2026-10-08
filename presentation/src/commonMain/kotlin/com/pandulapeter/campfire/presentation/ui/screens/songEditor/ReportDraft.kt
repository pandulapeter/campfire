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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.snapshotFlow
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel

/**
 * Keeps the view model's copy of the text in step with the field, without writing any of it. It is what tells the
 * app there is something unsaved here, and what it saves if the user asks for it on the way out - by which time
 * this screen, and the field with it, may already be gone.
 */
@Composable
internal fun ReportDraft(
    viewModel: CampfireViewModel,
    fileName: String,
    text: State<String>,
    textFieldState: TextFieldState,
) {
    LaunchedEffect(text, fileName) {
        snapshotFlow { text.value }.collect { viewModel.onEditorTextChanged(fileName, it) }
    }
    // Whatever became of the text - saved, discarded, or the song deleted - there is no draft once the editor is gone.
    DisposableEffect(fileName, textFieldState) {
        // Metadata sheets read this field at the moment they open, including edits not yet reported as a draft.
        viewModel.retainEditorField(fileName, textFieldState)
        onDispose { viewModel.onEditorClosed(fileName) }
    }
}
