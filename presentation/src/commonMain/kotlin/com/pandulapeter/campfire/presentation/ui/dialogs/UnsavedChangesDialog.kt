/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.cancel
import com.pandulapeter.campfire.presentation.resources.save
import com.pandulapeter.campfire.presentation.resources.song_editor_discard
import com.pandulapeter.campfire.presentation.resources.song_editor_unsaved_changes
import com.pandulapeter.campfire.presentation.resources.song_editor_unsaved_changes_confirmation
import com.pandulapeter.campfire.presentation.ui.components.saveShortcut

/**
 * Asked when the editor is left with text in it that has not been written yet. Three answers rather than the usual
 * two: the editor only ever writes when it is told to, so throwing what was typed away has to be asked for just as
 * explicitly as keeping it, and staying in the editor has to be possible without picking either. While the text is
 * being written only Cancel is left, which still means staying: the write is not something to be pressed twice, and
 * Discard would be answering a question the write is already answering.
 */
@Composable
internal fun UnsavedChangesDialog(
    isSaving: Boolean,
    onCancel: () -> Unit,
    onDiscard: () -> Unit,
    onSave: () -> Unit,
) {
    // A dialog is a window of its own, which holds no focus until one of its buttons is clicked: without a target
    // taken as it opens (requested from the title, inside the dialog), Ctrl / Cmd + S, the same key the editor saves
    // with, would go unheard.
    val focusRequester = remember { FocusRequester() }
    AlertDialog(
        modifier = Modifier
            .saveShortcut { if (!isSaving) onSave() }
            .focusRequester(focusRequester)
            .focusTarget(),
        onDismissRequest = onCancel,
        title = {
            // Here rather than next to the requester: on Android the dialog's content is composed a frame after the
            // composition that shows it, and a request made from there finds no target yet.
            LaunchedEffect(Unit) {
                withFrameNanos { }
                focusRequester.requestFocus()
            }
            Text(stringResource(Res.string.song_editor_unsaved_changes))
        },
        text = { Text(stringResource(Res.string.song_editor_unsaved_changes_confirmation)) },
        confirmButton = {
            TextButton(
                enabled = !isSaving,
                onClick = onSave,
            ) { Text(stringResource(Res.string.save)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onCancel) { Text(stringResource(Res.string.cancel)) }
                TextButton(
                    enabled = !isSaving,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    onClick = onDiscard,
                ) { Text(stringResource(Res.string.song_editor_discard)) }
            }
        },
    )
}
