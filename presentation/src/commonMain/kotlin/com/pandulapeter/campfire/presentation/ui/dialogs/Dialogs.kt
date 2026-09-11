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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.cancel
import com.pandulapeter.campfire.presentation.resources.create
import com.pandulapeter.campfire.presentation.resources.delete
import com.pandulapeter.campfire.presentation.resources.ic_add
import com.pandulapeter.campfire.presentation.resources.setlists_delete_setlist
import com.pandulapeter.campfire.presentation.resources.setlists_delete_setlist_confirmation
import com.pandulapeter.campfire.presentation.resources.setlists_new_setlist
import com.pandulapeter.campfire.presentation.resources.setlists_new_setlist_title
import com.pandulapeter.campfire.presentation.resources.setlists_rename
import com.pandulapeter.campfire.presentation.resources.setlists_rename_title
import com.pandulapeter.campfire.presentation.resources.settings_sync_disconnect
import com.pandulapeter.campfire.presentation.resources.settings_sync_disconnect_confirmation
import com.pandulapeter.campfire.presentation.resources.song_details_add_to_setlist
import com.pandulapeter.campfire.presentation.resources.song_details_tag_add
import com.pandulapeter.campfire.presentation.resources.song_details_tag_name
import com.pandulapeter.campfire.presentation.resources.song_details_tag_suggestions
import com.pandulapeter.campfire.presentation.resources.song_editor_discard
import com.pandulapeter.campfire.presentation.resources.song_editor_revert
import com.pandulapeter.campfire.presentation.resources.song_editor_revert_confirmation
import com.pandulapeter.campfire.presentation.resources.song_editor_save
import com.pandulapeter.campfire.presentation.resources.song_editor_unsaved_changes
import com.pandulapeter.campfire.presentation.resources.song_editor_unsaved_changes_confirmation
import com.pandulapeter.campfire.presentation.resources.songs_delete_song
import com.pandulapeter.campfire.presentation.resources.songs_delete_song_confirmation
import com.pandulapeter.campfire.presentation.resources.songs_new_song
import com.pandulapeter.campfire.presentation.resources.songs_new_song_artist
import com.pandulapeter.campfire.presentation.resources.songs_new_song_title
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.ActionListItem
import com.pandulapeter.campfire.presentation.ui.components.CheckboxListItem
import com.pandulapeter.campfire.presentation.ui.components.SettingsSectionTitle
import com.pandulapeter.campfire.presentation.ui.components.SongActions
import com.pandulapeter.campfire.presentation.ui.components.SongsControls
import com.pandulapeter.campfire.presentation.ui.components.TagFlowRow
import com.pandulapeter.campfire.presentation.ui.components.TagPill
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.SongDisplayControls
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import com.pandulapeter.campfire.presentation.localization.stringResource

/**
 * Hosts whichever dialog or bottom sheet the view model asks for.
 */
@Composable
internal fun CampfireDialogs(
    viewModel: CampfireViewModel,
    urlOpener: (String) -> Unit,
) {
    val visibleDialog by viewModel.visibleDialog.collectAsStateWithLifecycle()
    when (val dialog = visibleDialog) {
        CampfireViewModel.DialogType.NewSetlist -> TextInputDialog(
            title = stringResource(Res.string.setlists_new_setlist),
            label = stringResource(Res.string.setlists_new_setlist_title),
            confirmLabel = stringResource(Res.string.create),
            onDismiss = viewModel::dismissDialog,
            onConfirm = { title ->
                viewModel.createSetlist(title)
                viewModel.dismissDialog()
            },
        )

        is CampfireViewModel.DialogType.RenameSetlist -> TextInputDialog(
            title = stringResource(Res.string.setlists_rename),
            label = stringResource(Res.string.setlists_rename_title),
            initialValue = dialog.setlist.title,
            confirmLabel = stringResource(Res.string.setlists_rename),
            onDismiss = viewModel::dismissDialog,
            onConfirm = { title ->
                viewModel.renameSetlist(dialog.setlist, title)
                viewModel.dismissDialog()
            },
        )

        CampfireViewModel.DialogType.NewSong -> NewSongDialog(
            onDismiss = viewModel::dismissDialog,
            onCreate = { title, artist ->
                viewModel.createSong(title = title, artist = artist)
                viewModel.dismissDialog()
            },
        )

        CampfireViewModel.DialogType.SongsControls -> CampfireBottomSheet(onDismiss = viewModel::dismissDialog) {
            SongsControls(
                viewModel = viewModel,
                shouldIncludeSorting = true,
            )
        }

        CampfireViewModel.DialogType.SetlistsControls -> CampfireBottomSheet(onDismiss = viewModel::dismissDialog) {
            SongsControls(
                viewModel = viewModel,
                shouldIncludeSorting = false,
            )
        }

        is CampfireViewModel.DialogType.SetlistPicker -> SetlistPickerSheet(
            viewModel = viewModel,
            dialog = dialog,
        )

        is CampfireViewModel.DialogType.SongDisplayControls -> CampfireBottomSheet(onDismiss = viewModel::dismissDialog) {
            SongDisplayControls(
                viewModel = viewModel,
                dialog = dialog,
            )
        }

        is CampfireViewModel.DialogType.SongActions -> SongActionsSheet(
            viewModel = viewModel,
            dialog = dialog,
        )

        is CampfireViewModel.DialogType.DeleteSong -> ConfirmationDialog(
            title = stringResource(Res.string.songs_delete_song),
            text = stringResource(Res.string.songs_delete_song_confirmation, dialog.song.title),
            confirmLabel = stringResource(Res.string.delete),
            onDismiss = viewModel::dismissDialog,
            onConfirm = {
                viewModel.deleteSong(dialog.song.fileName)
                viewModel.dismissDialog()
            },
        )

        is CampfireViewModel.DialogType.AddSongTag -> AddSongTagDialog(
            viewModel = viewModel,
            dialog = dialog,
        )

        is CampfireViewModel.DialogType.DeleteSetlist -> ConfirmationDialog(
            title = stringResource(Res.string.setlists_delete_setlist),
            text = stringResource(Res.string.setlists_delete_setlist_confirmation, dialog.setlist.title),
            confirmLabel = stringResource(Res.string.delete),
            onDismiss = viewModel::dismissDialog,
            onConfirm = {
                viewModel.deleteSetlist(dialog.setlist.fileName)
                viewModel.dismissDialog()
            },
        )

        is CampfireViewModel.DialogType.DisconnectSync -> ConfirmationDialog(
            title = stringResource(Res.string.settings_sync_disconnect),
            text = stringResource(Res.string.settings_sync_disconnect_confirmation, dialog.accountName),
            confirmLabel = stringResource(Res.string.settings_sync_disconnect),
            onDismiss = viewModel::dismissDialog,
            onConfirm = {
                viewModel.disconnectSyncProvider()
                viewModel.dismissDialog()
            },
        )

        CampfireViewModel.DialogType.RevertChanges -> ConfirmationDialog(
            title = stringResource(Res.string.song_editor_revert),
            text = stringResource(Res.string.song_editor_revert_confirmation),
            confirmLabel = stringResource(Res.string.song_editor_revert),
            onDismiss = viewModel::dismissDialog,
            onConfirm = viewModel::revertEditorChanges,
        )

        CampfireViewModel.DialogType.UnsavedChanges -> UnsavedChangesDialog(
            onCancel = viewModel::dismissDialog,
            onDiscard = viewModel::leaveEditorWithoutSaving,
            onSave = viewModel::saveEditorChangesAndLeave,
        )

        null -> Unit
    }
}

/**
 * Asked when the editor is left with text in it that has not been written yet. Three answers rather than the usual
 * two: the editor only ever writes when it is told to, so throwing what was typed away has to be asked for just as
 * explicitly as keeping it, and staying in the editor has to be possible without picking either.
 */
@Composable
private fun UnsavedChangesDialog(
    onCancel: () -> Unit,
    onDiscard: () -> Unit,
    onSave: () -> Unit,
) = AlertDialog(
    onDismissRequest = onCancel,
    title = { Text(stringResource(Res.string.song_editor_unsaved_changes)) },
    text = { Text(stringResource(Res.string.song_editor_unsaved_changes_confirmation)) },
    confirmButton = {
        TextButton(onClick = onSave) { Text(stringResource(Res.string.song_editor_save)) }
    },
    dismissButton = {
        Row {
            TextButton(onClick = onCancel) { Text(stringResource(Res.string.cancel)) }
            TextButton(
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                onClick = onDiscard,
            ) { Text(stringResource(Res.string.song_editor_discard)) }
        }
    },
)

@Composable
private fun ConfirmationDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) = AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = { Text(text) },
    confirmButton = {
        TextButton(
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            onClick = onConfirm,
        ) { Text(confirmLabel) }
    },
    dismissButton = {
        TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
    },
)

/**
 * One required line of text and a confirm button that stays disabled until it has something in it. Creating and
 * renaming a setlist are the same dialog with different labels.
 */
@Composable
private fun TextInputDialog(
    title: String,
    label: String,
    initialValue: String = "",
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (value: String) -> Unit,
) {
    var value by rememberSaveable { mutableStateOf(initialValue) }
    val isValid = value.isNotBlank()
    val focusRequester = remember { FocusRequester() }
    // The dialog exists to take one line of text, so the caret is put in it instead of asking for one more tap.
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                value = value,
                onValueChange = { value = it.replace("\n", "").take(MAX_TITLE_LENGTH) },
                label = { Text(label) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (isValid) onConfirm(value) }),
            )
        },
        confirmButton = {
            TextButton(
                enabled = isValid,
                onClick = { onConfirm(value) },
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        },
    )
}

/**
 * The title and the artist of a song about to be created. Only the title is required: it is what the file is named
 * after, and a song without a known artist is a normal thing to have.
 */
@Composable
private fun NewSongDialog(
    onDismiss: () -> Unit,
    onCreate: (title: String, artist: String) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var artist by rememberSaveable { mutableStateOf("") }
    val isValid = title.isNotBlank()
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.songs_new_song)) },
        text = {
            Column {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    value = title,
                    onValueChange = { title = it.replace("\n", "").take(MAX_TITLE_LENGTH) },
                    label = { Text(stringResource(Res.string.songs_new_song_title)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = artist,
                    onValueChange = { artist = it.replace("\n", "").take(MAX_TITLE_LENGTH) },
                    label = { Text(stringResource(Res.string.songs_new_song_artist)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (isValid) onCreate(title, artist) }),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = isValid,
                onClick = { onCreate(title, artist) },
            ) { Text(stringResource(Res.string.create)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        },
    )
}

/**
 * One tag to put on a song, typed or picked. The suggestions are the tags the rest of the library already uses,
 * narrowed by whatever has been typed so far, because a library where the same idea is filed under "christmas",
 * "Christmas" and "xmas" is a library whose tags filter nothing.
 */
@Composable
private fun AddSongTagDialog(
    viewModel: CampfireViewModel,
    dialog: CampfireViewModel.DialogType.AddSongTag,
) {
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    var value by rememberSaveable { mutableStateOf("") }
    val isValid = value.isNotBlank()
    val focusRequester = remember { FocusRequester() }
    val addTag = { tag: String ->
        viewModel.setSongTag(fileName = dialog.song.fileName, tag = tag, isSelected = true)
        viewModel.dismissDialog()
    }
    val suggestions = remember(tags, dialog.song, value) {
        val songTags = dialog.song.tags.mapTo(mutableSetOf()) { it.lowercase() }
        tags.filter { it.name.lowercase() !in songTags && it.name.contains(value.trim(), ignoreCase = true) }
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    AlertDialog(
        onDismissRequest = viewModel::dismissDialog,
        title = { Text(stringResource(Res.string.song_details_tag_add)) },
        text = {
            Column {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    value = value,
                    onValueChange = { value = it.replace("\n", "").take(MAX_TAG_LENGTH) },
                    label = { Text(stringResource(Res.string.song_details_tag_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (isValid) addTag(value) }),
                )
                // However many tags a library has grown to, the ones that match what is being typed are the only
                // ones worth offering, and the list is scrolled rather than allowed to push the buttons off screen.
                if (suggestions.isNotEmpty()) {
                    Text(
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                        text = stringResource(Res.string.song_details_tag_suggestions),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    TagFlowRow(
                        modifier = Modifier.heightIn(max = MAX_SUGGESTIONS_HEIGHT).verticalScroll(rememberScrollState())
                    ) {
                        suggestions.forEach { tag ->
                            TagPill(
                                text = tag.name,
                                onClick = { addTag(tag.name) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = isValid,
                onClick = { addTag(value) },
            ) { Text(stringResource(Res.string.song_details_tag_add)) }
        },
        dismissButton = {
            TextButton(onClick = viewModel::dismissDialog) { Text(stringResource(Res.string.cancel)) }
        },
    )
}

/**
 * The actions of one song where there is no pointer to open a dropdown menu with, reached by long pressing the row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SongActionsSheet(
    viewModel: CampfireViewModel,
    dialog: CampfireViewModel.DialogType.SongActions,
) = CampfireBottomSheet(onDismiss = viewModel::dismissDialog) { sheetState, _ ->
    val coroutineScope = rememberCoroutineScope()
    SettingsSectionTitle(text = dialog.song.title)
    SongActions(
        viewModel = viewModel,
        song = dialog.song,
        setlistFileName = dialog.setlistFileName,
        shouldIncludeAddToSetlist = dialog.shouldIncludeAddToSetlist,
    ) { title, icon, isEnabled, onClick ->
        ActionListItem(
            title = title,
            icon = icon,
            isEnabled = isEnabled,
            isEmphasized = false,
            // The sheet gets out of the way before whatever the action opens lands on top of it. Hiding the sheet
            // by hand does not count as dismissing it, so the dialog state is cleared here too: left as it was, an
            // action that opens no dialog of its own (editing, exporting) would leave the invisible sheet's modal
            // layer over the screen, swallowing the next tap.
            onClick = {
                coroutineScope.launch { sheetState.hide() }.invokeOnCompletion {
                    viewModel.dismissDialog()
                    onClick()
                }
            },
        )
    }
    Spacer(modifier = Modifier.height(16.dp))
}

/**
 * The setlists one song can be put into, and the way to make a new one. Naming that new setlist happens in a dialog
 * on top of the sheet rather than instead of it: the setlist is only being created so that this song can go into it,
 * so the sheet staying where it is, with a ticked row appearing in it, is what says that it worked.
 */
@Composable
private fun SetlistPickerSheet(
    viewModel: CampfireViewModel,
    dialog: CampfireViewModel.DialogType.SetlistPicker,
) {
    val setlists by viewModel.setlists.collectAsStateWithLifecycle()
    var isNamingNewSetlist by rememberSaveable { mutableStateOf(false) }
    CampfireBottomSheet(onDismiss = viewModel::dismissDialog) {
        SettingsSectionTitle(text = stringResource(Res.string.song_details_add_to_setlist))
        setlists.forEach { setlist ->
            CheckboxListItem(
                title = setlist.title,
                isEnabled = dialog.currentSetlistFileName != setlist.fileName,
                isChecked = setlist.entries.any { it.songFileName == dialog.songFileName },
                onCheckedChange = { isChecked ->
                    if (isChecked) {
                        viewModel.addSongToSetlist(songFileName = dialog.songFileName, setlistFileName = setlist.fileName)
                    } else {
                        viewModel.removeSongFromSetlist(songFileName = dialog.songFileName, setlistFileName = setlist.fileName)
                    }
                },
            )
        }
        ActionListItem(
            title = stringResource(Res.string.setlists_new_setlist),
            icon = painterResource(Res.drawable.ic_add),
            onClick = { isNamingNewSetlist = true },
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
    if (isNamingNewSetlist) {
        TextInputDialog(
            title = stringResource(Res.string.setlists_new_setlist),
            label = stringResource(Res.string.setlists_new_setlist_title),
            confirmLabel = stringResource(Res.string.create),
            onDismiss = { isNamingNewSetlist = false },
            onConfirm = { title ->
                viewModel.createSetlistWithSong(title = title, songFileName = dialog.songFileName)
                isNamingNewSetlist = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CampfireBottomSheet(
    onDismiss: () -> Unit,
    content: @Composable (sheetState: SheetState, dismiss: () -> Unit) -> Unit,
) {
    // No partially expanded state: these sheets are short, and they open at their full height.
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val coroutineScope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
    ) {
        content(sheetState) {
            coroutineScope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CampfireBottomSheet(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) = CampfireBottomSheet(onDismiss) { _, _ -> content() }

private const val MAX_TITLE_LENGTH = 60
private const val MAX_TAG_LENGTH = 40
private val MAX_SUGGESTIONS_HEIGHT = 160.dp
