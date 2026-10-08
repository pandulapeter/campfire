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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.data.model.domain.SyncState
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.delete
import com.pandulapeter.campfire.presentation.resources.settings_library_delete
import com.pandulapeter.campfire.presentation.resources.settings_library_delete_confirmation
import com.pandulapeter.campfire.presentation.resources.settings_library_delete_confirmation_sync
import com.pandulapeter.campfire.presentation.resources.settings_library_delete_prompt
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.fadingTopEdge
import com.pandulapeter.campfire.presentation.ui.components.rememberClearTextButton
import com.pandulapeter.campfire.presentation.ui.platform.bounceVerticalScroll

/**
 * The one confirmation that is typed rather than tapped: every song and setlist on the device goes with it, and a
 * button in the place where every other dialog's confirmation sits is one a hand reaches out of habit. The word is
 * [DELETE_LIBRARY_CONFIRMATION] in every language, so that it is the same word whatever the app is set to, and what is
 * typed is put in capitals as it is typed, so that the only thing left to get right is the word itself.
 *
 * The sync sentence is there only while an account is connected, and in the error color: the deletion starts a run
 * that carries it to the cloud folder without asking again (`DeleteLibraryUseCase`), so every other device loses the
 * library too, and that is the part of the dialog somebody who means "this phone" must not skim past.
 */
@Composable
internal fun DeleteLibraryDialog(
    viewModel: CampfireViewModel,
) {
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val isImporting by viewModel.isImporting.collectAsStateWithLifecycle()
    val importReport by viewModel.importReport.collectAsStateWithLifecycle()
    var value by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    val isConfirmed = value.text.trim() == DELETE_LIBRARY_CONFIRMATION
    // Files dropped or opened with the app while the sheet is up are imported against the library it would delete, so
    // Delete waits, greyed, until that import and its question are over.
    val canDelete = isConfirmed && !isImporting && importReport !is CampfireViewModel.ImportReport.Review
    val focusRequester = rememberFirstFieldFocusRequester()
    val keyboardController = LocalSoftwareKeyboardController.current
    val scrollState = rememberScrollState()
    val confirmOnce = rememberSingleConfirmation()
    val deleteLibrary = { close: () -> Unit ->
        confirmOnce {
            viewModel.deleteLibrary()
            close()
        }
    }
    TextFieldBottomSheet(
        onDismissRequest = { viewModel.dismissSheet(CampfireViewModel.DialogType.DeleteLibrary) },
        title = stringResource(Res.string.settings_library_delete),
        text = { contentPadding ->
            val closeSheet = { close() }
            val isClosing = LocalIsSheetClosing.current
            Column(
                modifier = Modifier.fadingTopEdge(scrollState, sheetContainerColor()).bounceVerticalScroll(scrollState).padding(contentPadding),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(stringResource(Res.string.settings_library_delete_confirmation))
                if (syncState is SyncState.Connected) {
                    Text(
                        text = stringResource(Res.string.settings_library_delete_confirmation_sync),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    value = value,
                    onValueChange = { typed ->
                        // A letter whose capital is longer than itself (ß) makes the text longer than the selection
                        // was measured against.
                        val text = typed.text.asSingleLine().uppercase().take(MAX_DELETE_LIBRARY_CONFIRMATION_LENGTH)
                        value = typed.copy(
                            text = text,
                            selection = TextRange(typed.selection.start.coerceAtMost(text.length), typed.selection.end.coerceAtMost(text.length)),
                        )
                    },
                    label = { Text(stringResource(Res.string.settings_library_delete_prompt, DELETE_LIBRARY_CONFIRMATION)) },
                    trailingIcon = rememberClearTextButton(isVisible = value.text.isNotEmpty(), onClear = { value = TextFieldValue() }),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Done,
                    ),
                    // The close button leaves the keyboard up, so a Done during the slide that follows it is dropped.
                    keyboardActions = KeyboardActions(
                        onDone = {
                            when {
                                isClosing() -> Unit
                                canDelete -> deleteLibrary(closeSheet)
                                else -> keyboardController?.hide()
                            }
                        },
                    ),
                )
            }
        },
        confirmButton = { close ->
            BottomSheetConfirmButton(
                enabled = canDelete,
                isSaveShortcut = false,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
                onClick = { deleteLibrary(close) },
            ) { Text(stringResource(Res.string.delete)) }
        },
    )
}

private const val DELETE_LIBRARY_CONFIRMATION = "DELETE"

private const val MAX_DELETE_LIBRARY_CONFIRMATION_LENGTH = 30
