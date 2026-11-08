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
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import com.pandulapeter.campfire.presentation.ui.components.saveShortcut
import com.pandulapeter.campfire.presentation.ui.components.shortcuts.KeyShortcut
import com.pandulapeter.campfire.presentation.ui.screens.songEditor.search.EditorSearchMode
import com.pandulapeter.campfire.presentation.ui.screens.songEditor.search.editorSearchShortcuts

/**
 * Every key the whole editor hears, wherever in it the focus is: Save ([saveShortcut]), Find and Find and replace
 * ([editorSearchShortcuts]), the formatting keys while the field is shown ([isFieldShown], see
 * [editorFormattingShortcuts]), and undo and redo ([editorUndoShortcuts]).
 */
internal fun Modifier.editorShortcuts(
    textFieldState: TextFieldState,
    isFieldShown: Boolean,
    onSave: () -> Unit,
    onOpenSearch: (EditorSearchMode) -> Unit,
) = this
    .saveShortcut(onSave)
    .editorSearchShortcuts(
        onFind = { onOpenSearch(EditorSearchMode.FIND) },
        onReplace = { onOpenSearch(EditorSearchMode.REPLACE) },
    )
    .editorFormattingShortcuts(isEnabled = isFieldShown, onToggle = textFieldState::toggleFormatting)
    .editorUndoShortcuts(textFieldState)

/**
 * Ctrl / Cmd + Z undoes and Ctrl / Cmd + Shift + Z or Ctrl / Cmd + Y redoes in the field's own history, the one the title
 * row's buttons step, wherever in the editor the focus is: the field answers the keys only while it is focused, and
 * the preview pane, the editor's own focus target, and a toolbar button just pressed would otherwise leave them to
 * nothing. Heard before the field, so a focused field does not step the history a second time; the key-up is taken too,
 * like `editorSearchShortcuts`'s, and on the web the shell keeps the browser's history from Ctrl / Cmd + Y.
 */
@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.editorUndoShortcuts(textFieldState: TextFieldState) = onPreviewKeyEvent { keyEvent ->
    val undoState = textFieldState.undoState
    val action = when {
        KeyShortcut.UNDO.matches(keyEvent) -> undoState::undo
        KeyShortcut.REDO.matches(keyEvent) || KeyShortcut.REDO_ALTERNATE.matches(keyEvent) -> undoState::redo
        else -> null
    }
    if (action != null && keyEvent.type == KeyEventType.KeyDown) action()
    action != null
}
