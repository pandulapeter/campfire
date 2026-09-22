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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.cancel
import com.pandulapeter.campfire.presentation.resources.close
import com.pandulapeter.campfire.presentation.resources.create
import com.pandulapeter.campfire.presentation.resources.delete
import com.pandulapeter.campfire.presentation.resources.done
import com.pandulapeter.campfire.presentation.resources.ic_add
import com.pandulapeter.campfire.presentation.resources.ic_clear
import com.pandulapeter.campfire.presentation.resources.ic_search
import com.pandulapeter.campfire.presentation.resources.import_conflicts
import com.pandulapeter.campfire.presentation.resources.import_conflicts_confirm
import com.pandulapeter.campfire.presentation.resources.import_conflicts_duplicates
import com.pandulapeter.campfire.presentation.resources.import_conflicts_files
import com.pandulapeter.campfire.presentation.resources.import_conflicts_keep_both
import com.pandulapeter.campfire.presentation.resources.import_conflicts_keep_both_description
import com.pandulapeter.campfire.presentation.resources.import_conflicts_more
import com.pandulapeter.campfire.presentation.resources.import_conflicts_new
import com.pandulapeter.campfire.presentation.resources.import_conflicts_question
import com.pandulapeter.campfire.presentation.resources.import_conflicts_replace
import com.pandulapeter.campfire.presentation.resources.import_conflicts_replace_description
import com.pandulapeter.campfire.presentation.resources.import_conflicts_skip
import com.pandulapeter.campfire.presentation.resources.import_conflicts_skip_description
import com.pandulapeter.campfire.presentation.resources.import_conflicts_skipped
import com.pandulapeter.campfire.presentation.resources.import_conflicts_summary
import com.pandulapeter.campfire.presentation.resources.import_oversized
import com.pandulapeter.campfire.presentation.resources.save
import com.pandulapeter.campfire.presentation.resources.setlists_delete_setlist
import com.pandulapeter.campfire.presentation.resources.setlists_duplicate
import com.pandulapeter.campfire.presentation.resources.setlists_duplicate_title
import com.pandulapeter.campfire.presentation.resources.setlists_delete_setlist_confirmation
import com.pandulapeter.campfire.presentation.resources.setlists_description
import com.pandulapeter.campfire.presentation.resources.setlists_edit_setlist
import com.pandulapeter.campfire.presentation.resources.setlists_new_setlist
import com.pandulapeter.campfire.presentation.resources.setlists_new_setlist_title
import com.pandulapeter.campfire.presentation.resources.setlists_no_search_results
import com.pandulapeter.campfire.presentation.resources.setlists_search
import com.pandulapeter.campfire.presentation.resources.setlists_sort_and_filter
import com.pandulapeter.campfire.presentation.resources.setlists_song_assignments
import com.pandulapeter.campfire.presentation.resources.settings_sync_disconnect
import com.pandulapeter.campfire.presentation.resources.settings_sync_disconnect_confirmation
import com.pandulapeter.campfire.presentation.resources.songs_setlist_assignments
import com.pandulapeter.campfire.presentation.resources.song_details_display_options
import com.pandulapeter.campfire.presentation.resources.song_details_language
import com.pandulapeter.campfire.presentation.resources.song_details_language_no_search_results
import com.pandulapeter.campfire.presentation.resources.song_details_language_search
import com.pandulapeter.campfire.presentation.resources.song_details_tag_add
import com.pandulapeter.campfire.presentation.resources.song_details_tag_name
import com.pandulapeter.campfire.presentation.resources.song_details_tag_suggestions
import com.pandulapeter.campfire.presentation.resources.song_editor_discard
import com.pandulapeter.campfire.presentation.resources.song_editor_revert
import com.pandulapeter.campfire.presentation.resources.song_editor_revert_confirmation
import com.pandulapeter.campfire.presentation.resources.song_editor_unsaved_changes
import com.pandulapeter.campfire.presentation.resources.song_editor_unsaved_changes_confirmation
import com.pandulapeter.campfire.presentation.resources.songs_delete_song
import com.pandulapeter.campfire.presentation.resources.songs_delete_song_confirmation
import com.pandulapeter.campfire.presentation.resources.songs_new_song
import com.pandulapeter.campfire.presentation.resources.songs_new_song_artist
import com.pandulapeter.campfire.presentation.resources.songs_new_song_title
import com.pandulapeter.campfire.presentation.resources.songs_clear
import com.pandulapeter.campfire.presentation.resources.songs_empty_title
import com.pandulapeter.campfire.presentation.resources.songs_no_search_results
import com.pandulapeter.campfire.presentation.resources.songs_search
import com.pandulapeter.campfire.presentation.resources.songs_sort_and_filter
import com.pandulapeter.campfire.data.model.domain.ImportConflictResolution
import com.pandulapeter.campfire.data.model.domain.ImportPlan
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.SongLanguage
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.ActionListItem
import com.pandulapeter.campfire.presentation.ui.components.CheckboxListItem
import com.pandulapeter.campfire.presentation.ui.components.MAX_SEARCH_QUERY_LENGTH
import com.pandulapeter.campfire.presentation.ui.components.PickableLanguage
import com.pandulapeter.campfire.presentation.ui.components.RadioListItem
import com.pandulapeter.campfire.presentation.ui.components.SettingsSectionTitle
import com.pandulapeter.campfire.presentation.ui.components.SetlistsControls
import com.pandulapeter.campfire.presentation.ui.components.SongsControls
import com.pandulapeter.campfire.presentation.ui.components.TagFlowRow
import com.pandulapeter.campfire.presentation.ui.components.TagPill
import com.pandulapeter.campfire.presentation.ui.components.languageName
import com.pandulapeter.campfire.presentation.ui.components.pickableLanguages
import com.pandulapeter.campfire.presentation.ui.components.textResource
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.SongDisplayControls
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import com.pandulapeter.campfire.presentation.localization.currentLanguage
import com.pandulapeter.campfire.presentation.localization.pluralStringResource
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
        CampfireViewModel.DialogType.NewSetlist -> SetlistDetailsDialog(
            title = stringResource(Res.string.setlists_new_setlist),
            confirmLabel = stringResource(Res.string.create),
            onDismiss = viewModel::dismissDialog,
            // Dismissed before the setlist is created rather than after, since creating it is what opens the song
            // picker for it, and a dismissal arriving after that would close the picker instead.
            onConfirm = { setlistTitle, description ->
                viewModel.dismissDialog()
                viewModel.createSetlist(title = setlistTitle, description = description)
            },
        )

        is CampfireViewModel.DialogType.EditSetlist -> SetlistDetailsDialog(
            title = stringResource(Res.string.setlists_edit_setlist),
            initialTitle = dialog.setlist.title,
            initialDescription = dialog.setlist.description,
            confirmLabel = stringResource(Res.string.save),
            onDismiss = viewModel::dismissDialog,
            onConfirm = { setlistTitle, description ->
                viewModel.editSetlist(setlistFileName = dialog.setlist.fileName, title = setlistTitle, description = description)
                viewModel.dismissDialog()
            },
        )

        // Named before it is made rather than after: two setlists can carry the same title (a setlist is identified
        // by its file name), so a copy nobody named would sit under the original's title until somebody noticed.
        is CampfireViewModel.DialogType.DuplicateSetlist -> SetlistDetailsDialog(
            title = stringResource(Res.string.setlists_duplicate),
            initialTitle = textResource(Res.string.setlists_duplicate_title, dialog.setlist.title),
            initialDescription = dialog.setlist.description,
            confirmLabel = stringResource(Res.string.setlists_duplicate),
            onDismiss = viewModel::dismissDialog,
            onConfirm = { setlistTitle, description ->
                viewModel.duplicateSetlist(setlist = dialog.setlist, title = setlistTitle, description = description)
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

        CampfireViewModel.DialogType.SongsControls -> CampfireBottomSheet(
            title = stringResource(Res.string.songs_sort_and_filter),
            onDismiss = { viewModel.dismissSheet(CampfireViewModel.DialogType.SongsControls) },
        ) { contentPadding ->
            SongsControls(
                viewModel = viewModel,
                contentPadding = contentPadding,
            )
        }

        CampfireViewModel.DialogType.SetlistsControls -> CampfireBottomSheet(
            title = stringResource(Res.string.setlists_sort_and_filter),
            onDismiss = { viewModel.dismissSheet(CampfireViewModel.DialogType.SetlistsControls) },
        ) { contentPadding ->
            SetlistsControls(
                viewModel = viewModel,
                contentPadding = contentPadding,
            )
        }

        is CampfireViewModel.DialogType.SetlistPicker -> SetlistPicker(
            viewModel = viewModel,
            dialog = dialog,
        )

        is CampfireViewModel.DialogType.SongPicker -> SongPicker(
            viewModel = viewModel,
            dialog = dialog,
        )

        is CampfireViewModel.DialogType.SongDisplayControls -> CampfireBottomSheet(
            title = stringResource(Res.string.song_details_display_options),
            onDismiss = { viewModel.dismissSheet(dialog) },
        ) { contentPadding ->
            SongDisplayControls(
                viewModel = viewModel,
                dialog = dialog,
                contentPadding = contentPadding,
            )
        }

        is CampfireViewModel.DialogType.DeleteSong -> ConfirmationDialog(
            title = stringResource(Res.string.songs_delete_song),
            text = textResource(Res.string.songs_delete_song_confirmation, dialog.song.title),
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

        is CampfireViewModel.DialogType.SongLanguages -> SongLanguagesDialog(
            viewModel = viewModel,
            dialog = dialog,
        )

        is CampfireViewModel.DialogType.DeleteSetlist -> ConfirmationDialog(
            title = stringResource(Res.string.setlists_delete_setlist),
            text = textResource(Res.string.setlists_delete_setlist_confirmation, dialog.setlist.title),
            confirmLabel = stringResource(Res.string.delete),
            onDismiss = viewModel::dismissDialog,
            onConfirm = {
                viewModel.deleteSetlist(dialog.setlist.fileName)
                viewModel.dismissDialog()
            },
        )

        is CampfireViewModel.DialogType.DisconnectSync -> ConfirmationDialog(
            title = stringResource(Res.string.settings_sync_disconnect),
            text = textResource(Res.string.settings_sync_disconnect_confirmation, dialog.accountName),
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

        is CampfireViewModel.DialogType.ImportConflicts -> ImportConflictsDialog(
            summary = dialog.summary,
            onCancel = viewModel::cancelImport,
            onConfirm = viewModel::resolveImport,
        )

        CampfireViewModel.DialogType.UnsavedChanges -> UnsavedChangesDialog(
            isSaving = viewModel.isSavingSong.collectAsStateWithLifecycle().value,
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
 * explicitly as keeping it, and staying in the editor has to be possible without picking either. While the text is
 * being written only Cancel is left, which still means staying: the write is not something to be pressed twice, and
 * Discard would be answering a question the write is already answering.
 */
@Composable
private fun UnsavedChangesDialog(
    isSaving: Boolean,
    onCancel: () -> Unit,
    onDiscard: () -> Unit,
    onSave: () -> Unit,
) = AlertDialog(
    onDismissRequest = onCancel,
    title = { Text(stringResource(Res.string.song_editor_unsaved_changes)) },
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

/**
 * The one question an import can raise, asked once for the whole batch: the file name is a song's identity, so a
 * file arriving under a name the library has already given to something else is either a second copy of it or a
 * different song, and only the user can say which. Files that are new, and files the library already holds
 * unchanged, are decided without asking and are summarised here only so that the answer is given in full sight of
 * what the import is otherwise about to do.
 *
 * One answer covers every conflict rather than one question per file, because the batch this exists for is an
 * archive of hundreds - being asked three hundred times is not a choice, it is an obstacle.
 */
@Composable
private fun ImportConflictsDialog(
    summary: ImportPlan.Summary,
    onCancel: () -> Unit,
    onConfirm: (ImportConflictResolution) -> Unit,
) {
    var resolution by rememberSaveable { mutableStateOf(ImportConflictResolution.KEEP_BOTH) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(Res.string.import_conflicts)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    pluralStringResource(
                        Res.plurals.import_conflicts_summary,
                        summary.conflictingFileNames.size,
                        summary.conflictingFileNames.size,
                    ),
                )
                Spacer(modifier = Modifier.height(8.dp))
                ImportConflictsFileNames(fileNames = summary.conflictingFileNames)
                if (summary.newSongCount > 0 || summary.newSetlistCount > 0) {
                    ImportConflictsNote(stringResource(Res.string.import_conflicts_new, summary.newSongCount, summary.newSetlistCount))
                }
                if (summary.duplicateCount > 0) {
                    ImportConflictsNote(pluralStringResource(Res.plurals.import_conflicts_duplicates, summary.duplicateCount, summary.duplicateCount))
                }
                if (summary.skippedCount > 0) {
                    ImportConflictsNote(pluralStringResource(Res.plurals.import_conflicts_skipped, summary.skippedCount, summary.skippedCount))
                }
                if (summary.oversizedCount > 0) {
                    ImportConflictsNote(pluralStringResource(Res.plurals.import_oversized, summary.oversizedCount, summary.oversizedCount))
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(Res.string.import_conflicts_question),
                    style = MaterialTheme.typography.titleSmall,
                )
                ImportConflictResolution.entries.forEach { option ->
                    RadioListItem(
                        title = stringResource(option.label),
                        description = stringResource(option.description),
                        isSelected = option == resolution,
                        onSelected = { resolution = option },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(resolution) }) { Text(stringResource(Res.string.import_conflicts_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(stringResource(Res.string.cancel)) }
        },
    )
}

/**
 * The conflicting names themselves, capped: the point is to recognise what is about to be decided about, and a list
 * of three hundred file names inside a dialog is not something anybody reads.
 */
@Composable
private fun ImportConflictsFileNames(fileNames: List<String>) = Column {
    Text(
        text = stringResource(Res.string.import_conflicts_files),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    fileNames.take(MAXIMUM_LISTED_CONFLICTS).forEach { fileName ->
        Text(
            text = fileName,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    if (fileNames.size > MAXIMUM_LISTED_CONFLICTS) {
        Text(
            text = stringResource(Res.string.import_conflicts_more, fileNames.size - MAXIMUM_LISTED_CONFLICTS),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ImportConflictsNote(text: String) = Text(
    modifier = Modifier.padding(top = 8.dp),
    text = text,
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
)

private val ImportConflictResolution.label
    get() = when (this) {
        ImportConflictResolution.KEEP_BOTH -> Res.string.import_conflicts_keep_both
        ImportConflictResolution.REPLACE -> Res.string.import_conflicts_replace
        ImportConflictResolution.SKIP -> Res.string.import_conflicts_skip
    }

private val ImportConflictResolution.description
    get() = when (this) {
        ImportConflictResolution.KEEP_BOTH -> Res.string.import_conflicts_keep_both_description
        ImportConflictResolution.REPLACE -> Res.string.import_conflicts_replace_description
        ImportConflictResolution.SKIP -> Res.string.import_conflicts_skip_description
    }

private const val MAXIMUM_LISTED_CONFLICTS = 5

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
 * The [FocusRequester] of the field a dialog opens onto. A dialog that is there to be typed into puts the caret in
 * its first field rather than asking for one more tap, which on a touch platform is also what brings the keyboard
 * up with it - and every dialog here that holds a text field holds it as the first thing under the title, so there
 * is only ever the one field to open on.
 */
@Composable
private fun rememberFirstFieldFocusRequester(): FocusRequester {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    return focusRequester
}

/**
 * Lets a dialog's confirmation through the first time and never again. A dialog that confirms does not leave with
 * the tap but with the next frame, and until then its button and the keyboard's Done key are both still live: on a
 * frame that comes late a second tap lands on a dialog that has already been answered, and creates the song or
 * the setlist a second time. The state is written as the event is handled, so the second event of the same frame
 * already reads it.
 */
@Composable
private fun rememberSingleConfirmation(): (confirm: () -> Unit) -> Unit {
    var hasConfirmed by remember { mutableStateOf(false) }
    return { confirm ->
        if (!hasConfirmed) {
            hasConfirmed = true
            confirm()
        }
    }
}

/**
 * Everything the user gets to say about a setlist: its title, and the description that goes under its header on the
 * setlists screen. Creating one, editing one and naming a copy of one are the same dialog with different labels,
 * since all three are answering the same two questions.
 *
 * Only the title is required. The description is what somebody writes for their own sake ("acoustic, two sets, no
 * encore"), and most setlists never get one - but the setlists screen's search reads it, so a setlist that is hard
 * to name can still be found by what it is for.
 */
@Composable
private fun SetlistDetailsDialog(
    title: String,
    initialTitle: String = "",
    initialDescription: String = "",
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (title: String, description: String) -> Unit,
) {
    // A TextFieldValue rather than a String, for the selection: a dialog that opens on a title the user is meant to
    // replace ("Summer set (copy)", the title being renamed) has all of it selected, so the first key typed writes the
    // new name instead of appending to the old one. Nothing is lost by it either, since a tap or an arrow key puts the
    // caret where it was aimed. The description is opened on for editing rather than for replacing, so its caret goes
    // to the end of what is already written instead.
    var setlistTitle by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(text = initialTitle, selection = TextRange(0, initialTitle.length)))
    }
    var description by rememberSaveable { mutableStateOf(initialDescription) }
    val isValid = setlistTitle.text.isNotBlank()
    val focusRequester = rememberFirstFieldFocusRequester()
    val confirmOnce = rememberSingleConfirmation()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    value = setlistTitle,
                    onValueChange = { newValue ->
                        val text = newValue.text.replace("\n", "").take(MAX_TITLE_LENGTH)
                        // Rebuilt only where the text had to be cut, or every keystroke would throw away the
                        // selection the field is reporting - which is the caret itself, and the run of text a drag
                        // is picking out.
                        setlistTitle = if (text == newValue.text) {
                            newValue
                        } else {
                            TextFieldValue(text = text, selection = TextRange(newValue.selection.end.coerceAtMost(text.length)))
                        }
                    },
                    label = { Text(stringResource(Res.string.setlists_new_setlist_title)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
                Spacer(modifier = Modifier.height(8.dp))
                // Several lines rather than one, and no Done action on the keyboard: this is a sentence somebody
                // writes about a setlist, and the return key belongs to it rather than to the dialog.
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = description,
                    onValueChange = { description = it.take(MAX_DESCRIPTION_LENGTH) },
                    label = { Text(stringResource(Res.string.setlists_description)) },
                    minLines = DESCRIPTION_LINES,
                    maxLines = DESCRIPTION_LINES,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = isValid,
                onClick = { confirmOnce { onConfirm(setlistTitle.text, description) } },
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
    val focusRequester = rememberFirstFieldFocusRequester()
    val confirmOnce = rememberSingleConfirmation()
    val create = { if (isValid) confirmOnce { onCreate(title, artist) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.songs_new_song)) },
        text = {
            Column {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    value = title,
                    onValueChange = { title = it.asSingleLine().take(MAX_TITLE_LENGTH) },
                    label = { Text(stringResource(Res.string.songs_new_song_title)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = artist,
                    onValueChange = { artist = it.asSingleLine().take(MAX_TITLE_LENGTH) },
                    label = { Text(stringResource(Res.string.songs_new_song_artist)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { create() }),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = isValid,
                onClick = create,
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
    val focusRequester = rememberFirstFieldFocusRequester()
    val addTag = { tag: String ->
        viewModel.setSongTag(fileName = dialog.song.fileName, tag = tag, isSelected = true)
        viewModel.dismissDialog()
    }
    val searchableTags = remember(tags) { tags.map { it to viewModel.normalizeForSearch(it.name) } }
    val suggestions = remember(searchableTags, dialog.song, value) {
        val songTags = dialog.song.tags.mapTo(mutableSetOf()) { it.lowercase() }
        val normalizedValue = viewModel.normalizeForSearch(value)
        searchableTags.mapNotNull { (tag, name) -> tag.takeIf { it.name.lowercase() !in songTags && normalizedValue in name } }
    }
    AlertDialog(
        onDismissRequest = viewModel::dismissDialog,
        title = { Text(stringResource(Res.string.song_details_tag_add)) },
        text = {
            Column {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    value = value,
                    onValueChange = { value = it.asSingleLine().take(MAX_TAG_LENGTH) },
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
 * The languages of one song, asked about all at once: the whole set is written when the dialog is confirmed, so a
 * file the user owns is rewritten once rather than once per checkbox.
 *
 * The list is ordered by the name the platform gives each language in the language the app is set to (see
 * [languageName]), with the ones the song already declares held at the top for as long as the dialog is open: a row
 * that reordered itself under the finger that has just ticked it would be worse than a list that has to be scrolled.
 *
 * What is listed before anything is typed is what can be named, plus the languages the song and the library already
 * use — and those come first, since the next song to be filed is far likelier to be in one of them than in any of
 * the six hundred the library has never held. Everything else — on the web that is most languages, see
 * [pickableLanguages] — is found by typing its code, which is also what such a row is labelled with. A language
 * nobody can name is still a language the file can be filed under, and the code is the one thing the app always
 * knows about it.
 */
@Composable
private fun SongLanguagesDialog(
    viewModel: CampfireViewModel,
    dialog: CampfireViewModel.DialogType.SongLanguages,
) {
    val appLanguageCode = currentLanguage.value.code
    val libraryLanguages by viewModel.languages.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    // Saved, since the dialog outlives a recreated Activity and Done writes whatever is ticked at that moment.
    var selectedCodes by rememberSaveable(
        dialog.song.fileName,
        stateSaver = listSaver<Set<String>, String>(save = { it.toList() }, restore = { it.toSet() }),
    ) { mutableStateOf(dialog.song.languages.toSet()) }
    val languages = remember(dialog.song, libraryLanguages, appLanguageCode) {
        val declared = dialog.song.languages
        val alsoOffer = declared + libraryLanguages.map { it.code }.filterNot { it == SongLanguage.UNKNOWN }
        val pickable = pickableLanguages(appLanguageCode = appLanguageCode, alsoOffer = alsoOffer, normalize = viewModel::normalize)
        // The song's own languages come first and stay there, in the order the file lists them.
        declared.mapNotNull { code -> pickable.firstOrNull { it.code == code } } + pickable.filterNot { it.code in declared }
    }
    val focusRequester = rememberFirstFieldFocusRequester()
    val matches = remember(languages, query) {
        val normalizedQuery = viewModel.normalize(query)
        // What was typed may be a code, and not the one the library files the language under: `HUN`, `hu-HU` and
        // `hu` all name Hungarian, and whichever of them a reader knows has to find the single row that is.
        val queryCode = viewModel.languageCode(query)
        if (normalizedQuery.isEmpty()) {
            languages.filter { it.isListed }
        } else {
            languages.filter { it.code == queryCode || normalizedQuery in it.sortKey || it.code.startsWith(normalizedQuery) }
        }
    }
    AlertDialog(
        onDismissRequest = viewModel::dismissDialog,
        title = { Text(stringResource(Res.string.song_details_language)) },
        text = {
            Column {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    value = query,
                    onValueChange = { query = it.replace("\n", "").take(MAX_SEARCH_QUERY_LENGTH) },
                    label = { Text(stringResource(Res.string.song_details_language_search)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                )
                if (matches.isEmpty()) {
                    Text(
                        modifier = Modifier.padding(top = 16.dp),
                        text = stringResource(Res.string.song_details_language_no_search_results),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.padding(top = 8.dp).heightIn(max = MAX_LANGUAGES_HEIGHT)
                    ) {
                        items(
                            items = matches,
                            key = { it.code },
                        ) { language ->
                            CheckboxListItem(
                                title = language.label,
                                // The code is under the name, and is the name itself where there is none to put above it.
                                description = language.name?.let { language.code.uppercase() },
                                isChecked = language.code in selectedCodes,
                                onCheckedChange = { isChecked ->
                                    selectedCodes = if (isChecked) selectedCodes + language.code else selectedCodes - language.code
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    viewModel.setSongLanguages(fileName = dialog.song.fileName, codes = selectedCodes.toList())
                    viewModel.dismissDialog()
                },
            ) { Text(stringResource(Res.string.done)) }
        },
        dismissButton = {
            TextButton(onClick = viewModel::dismissDialog) { Text(stringResource(Res.string.cancel)) }
        },
    )
}

/**
 * The setlists one song can be put into, and the way to make a new one. Naming that new setlist happens in a dialog
 * on top of the sheet rather than instead of it: the setlist is only being created so that this song can go into it,
 * so the sheet staying where it is, with a ticked row appearing in it, is what says that it worked.
 *
 * A library that holds no setlists at all skips the sheet and asks for the name of the first one straight away,
 * since a sheet offering nothing to tick is one tap in the way of the only thing that can be done there. Whether
 * that is what happened is decided once, as the dialog opens ([isCreatingFirstSetlist]) rather than read from the
 * list as it stands: creating the setlist fills the list, and the sheet must not slide in behind a dialog that is
 * on its way out. It is also what the naming dialog is closed by, since there is nothing behind it to return to.
 */
@Composable
private fun SetlistPicker(
    viewModel: CampfireViewModel,
    dialog: CampfireViewModel.DialogType.SetlistPicker,
) {
    val setlists by viewModel.setlists.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    // An archived setlist has been put away, so it is not offered here - unless the song is in it already, which is
    // the only thing this sheet could still have to say about one. Sorted by title whatever the setlists screen is
    // sorted by, since this sheet is where a setlist is looked up by its name.
    val pickableSetlists = remember(setlists, dialog.song.fileName) {
        setlists
            .filter { setlist -> !setlist.isArchived || setlist.entries.any { it.songFileName == dialog.song.fileName } }
            .sortedWith(compareBy({ viewModel.normalize(it.title) }, { it.fileName }))
    }
    // Answered by the title or the description, the way the setlists screen's own search answers, but not by the
    // songs inside: the song this sheet is about is the only one that matters here.
    val matches = remember(pickableSetlists, query) {
        val normalizedQuery = viewModel.normalizeForSearch(query)
        pickableSetlists.filter { setlist ->
            normalizedQuery in viewModel.normalizeForSearch(setlist.title) || normalizedQuery in viewModel.normalizeForSearch(setlist.description)
        }
    }
    val isCreatingFirstSetlist = rememberSaveable { setlists.isEmpty() }
    var isNamingNewSetlist by rememberSaveable { mutableStateOf(isCreatingFirstSetlist) }
    val closeNamingDialog = { if (isCreatingFirstSetlist) viewModel.dismissDialog() else isNamingNewSetlist = false }
    if (!isCreatingFirstSetlist) {
        CampfireBottomSheet(
            title = dialog.song.title,
            subtitle = dialog.song.artist,
            onDismiss = { viewModel.dismissSheet(dialog) },
        ) { contentPadding ->
            SheetSectionTitle(text = stringResource(Res.string.songs_setlist_assignments))
            PickerSearchField(
                query = query,
                placeholder = stringResource(Res.string.setlists_search),
                onQueryChange = { query = it },
            )
            PickerList(
                contentPadding = contentPadding,
                noResultsText = if (matches.isEmpty() && query.isNotBlank()) stringResource(Res.string.setlists_no_search_results) else null,
            ) {
                items(
                    items = matches,
                    key = { it.fileName },
                ) { setlist ->
                    CheckboxListItem(
                        title = setlist.title,
                        isEnabled = dialog.lockedSetlistFileName != setlist.fileName,
                        isChecked = setlist.entries.any { it.songFileName == dialog.song.fileName },
                        onCheckedChange = { isChecked ->
                            if (isChecked) {
                                viewModel.addSongToSetlist(songFileName = dialog.song.fileName, setlistFileName = setlist.fileName)
                            } else {
                                viewModel.removeSongFromSetlist(songFileName = dialog.song.fileName, setlistFileName = setlist.fileName)
                            }
                        },
                    )
                }
                item(key = "new_setlist") {
                    ActionListItem(
                        title = stringResource(Res.string.setlists_new_setlist),
                        icon = painterResource(Res.drawable.ic_add),
                        onClick = { isNamingNewSetlist = true },
                    )
                }
            }
        }
    }
    if (isNamingNewSetlist) {
        SetlistDetailsDialog(
            title = stringResource(Res.string.setlists_new_setlist),
            // A search that found nothing is most likely the name of the setlist that is missing, and it opens
            // selected, so typing something else instead costs nothing.
            initialTitle = query.trim(),
            confirmLabel = stringResource(Res.string.create),
            onDismiss = closeNamingDialog,
            onConfirm = { setlistTitle, description ->
                viewModel.createSetlistWithSong(title = setlistTitle, description = description, songFileName = dialog.song.fileName)
                closeNamingDialog()
            },
        )
    }
}

/**
 * Every song in the library with a box each, which is how a setlist is filled from its own side: the setlist picker
 * puts one song into any number of setlists, and this puts any number of songs into one setlist.
 *
 * What is ticked is held here rather than read back from the setlist, and the whole of it is written on every tick
 * ([CampfireViewModel.setSetlistSongs]): a list of songs is ticked faster than a write comes back through the
 * library, and boxes that followed the library would each flick back for the length of a round trip. A song ticked
 * here goes to the end of the setlist, so a setlist built from nothing is in the order its songs were picked, and an
 * entry whose file has gone missing stays in it, since it is not listed here to be unticked.
 *
 * The songs the setlist already holds come first, in the setlist's own order, and stay there for as long as the sheet
 * is open for the reason the language picker keeps its own at the top: a row that jumped away from under the finger
 * that had just ticked it would be worse than a list that has to be scrolled. The rest are in alphabetical order,
 * whatever the songs screen is sorted by, since this is a list to find a song in rather than to browse.
 */
@Composable
private fun SongPicker(
    viewModel: CampfireViewModel,
    dialog: CampfireViewModel.DialogType.SongPicker,
) {
    val setlists by viewModel.setlists.collectAsStateWithLifecycle()
    val songs by viewModel.allSongs.collectAsStateWithLifecycle()
    val setlist = setlists.firstOrNull { it.fileName == dialog.setlist.fileName } ?: dialog.setlist
    // Seeded from the setlist as the library has it and saved, rather than from the snapshot the dialog was opened
    // with: the dialog outlives a recreated Activity, and a selection seeded again from that snapshot would drop
    // every song ticked before it from the next write.
    val initialSongFileNames = rememberSaveable(setlist.fileName) { setlist.entries.map { it.songFileName }.distinct() }
    var selectedSongFileNames by rememberSaveable(setlist.fileName) { mutableStateOf(initialSongFileNames) }
    var query by rememberSaveable { mutableStateOf("") }
    // Normalized once per library rather than once per keystroke, since the search runs over every song on every
    // character typed.
    val pickableSongs = remember(songs, initialSongFileNames) {
        val pickable = songs.map { song ->
            PickableSong(
                song = song,
                title = viewModel.normalizeForSearch(song.title),
                artist = viewModel.normalizeForSearch(song.artist),
            )
        }
        val pickableByFileName = pickable.associateBy { it.song.fileName }
        val initial = initialSongFileNames.toSet()
        initialSongFileNames.mapNotNull { pickableByFileName[it] } +
            pickable.filterNot { it.song.fileName in initial }
                .sortedWith(compareBy({ viewModel.normalize(it.song.title) }, { viewModel.normalize(it.song.artist) }, { it.song.fileName }))
    }
    val matches = remember(pickableSongs, query) {
        val normalizedQuery = viewModel.normalizeForSearch(query)
        pickableSongs.filter { normalizedQuery in it.title || normalizedQuery in it.artist }
    }
    CampfireBottomSheet(
        title = setlist.title,
        subtitle = setlist.description,
        onDismiss = { viewModel.dismissSheet(dialog) },
    ) { contentPadding ->
        SheetSectionTitle(text = stringResource(Res.string.setlists_song_assignments))
        PickerSearchField(
            query = query,
            placeholder = stringResource(Res.string.songs_search),
            onQueryChange = { query = it },
        )
        PickerList(
            contentPadding = contentPadding,
            noResultsText = when {
                // Reached from an empty setlist's "Add songs" in an empty library, which the setlists screen lists as well.
                songs.isEmpty() -> stringResource(Res.string.songs_empty_title)
                matches.isEmpty() && query.isNotBlank() -> stringResource(Res.string.songs_no_search_results)
                else -> null
            },
        ) {
            items(
                items = matches,
                key = { it.song.fileName },
            ) { pickableSong ->
                val fileName = pickableSong.song.fileName
                CheckboxListItem(
                    title = pickableSong.song.title,
                    description = pickableSong.song.artist.ifBlank { null },
                    isChecked = fileName in selectedSongFileNames,
                    onCheckedChange = { isChecked ->
                        selectedSongFileNames = if (isChecked) selectedSongFileNames + fileName else selectedSongFileNames - fileName
                        viewModel.setSetlistSongs(setlistFileName = setlist.fileName, songFileNames = selectedSongFileNames)
                    },
                )
            }
        }
    }
}

/** A song of the [SongPicker] with the title and the artist its search compares, normalized. */
private class PickableSong(
    val song: Song,
    val title: String,
    val artist: String,
)

@Composable
private fun SheetSectionTitle(text: String) = SettingsSectionTitle(
    text = text,
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
)

/**
 * The search field of a picker sheet. Unlike the field of a dialog it is not focused as the sheet opens: the list
 * under it is what the sheet is opened for, and on a touch platform the keyboard would cover half of that list
 * before anybody had decided to search it.
 */
@Composable
private fun PickerSearchField(
    query: String,
    placeholder: String,
    onQueryChange: (String) -> Unit,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        value = query,
        onValueChange = { onQueryChange(it.replace("\n", "").take(MAX_SEARCH_QUERY_LENGTH)) },
        placeholder = { Text(placeholder) },
        leadingIcon = {
            Icon(
                painter = painterResource(Res.drawable.ic_search),
                contentDescription = null,
            )
        },
        // Always given a slot, with the button fading in and out of it, so that the text is not pushed around by
        // the slot itself appearing with the first character.
        trailingIcon = {
            AnimatedVisibility(
                visible = query.isNotEmpty(),
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
            ) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_clear),
                        contentDescription = stringResource(Res.string.songs_clear),
                    )
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
    )
}

/**
 * The scrolling part of a picker sheet, which takes whatever height the sheet has left under its header rather than
 * the height of everything it could list - a library of songs is far taller than any screen.
 *
 * It never gets shorter while the sheet is open, only taller: narrowing the list by a search would otherwise shrink
 * the sheet with every character typed, moving the field being typed into down the screen along with it.
 *
 * @param contentPadding What the sheet's content keeps clear at the bottom, applied inside the scroll so that the last
 *   rows pass under the navigation bar and the keyboard on their way up.
 * @param noResultsText What to say in place of the rows, null while there is nothing to say.
 */
@Composable
private fun ColumnScope.PickerList(
    contentPadding: PaddingValues,
    noResultsText: String?,
    content: LazyListScope.() -> Unit,
) {
    val density = LocalDensity.current
    var tallestHeight by remember { mutableIntStateOf(0) }
    LazyColumn(
        modifier = Modifier
            .weight(1f, fill = false)
            .heightIn(min = with(density) { tallestHeight.toDp() })
            .onSizeChanged { tallestHeight = maxOf(tallestHeight, it.height) },
        // The gap under the search field is the list's own content padding rather than a padding around the list, so
        // that a scrolled row goes under the field itself instead of being cut off a few pixels short of it.
        contentPadding = PaddingValues(
            top = PICKER_LIST_TOP_PADDING,
            bottom = contentPadding.calculateBottomPadding(),
        ),
    ) {
        if (noResultsText != null) {
            item(key = "no_results") {
                Text(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    text = noResultsText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        content()
    }
}

/**
 * Every sheet of the app, drawn edge to edge: the sheet runs down under the navigation bar and the keyboard instead
 * of stopping above them, and only its content is kept clear of them. [ModalBottomSheet] pads the whole content by
 * the bottom inset by default, which leaves a scrolling list ending on a band of the sheet's color above the bar
 * rather than scrolling on under it, so that inset is left out of the sheet's own insets and handed to the content
 * instead, as the `contentPadding` scrolling content applies inside its scroll and the rest leaves under its last row.
 * The top inset stays with the sheet, which only pads by it once it has been dragged up against the status bar.
 *
 * @param title What the sheet is about, named in its [SheetHeader].
 * @param subtitle A line under [title], left out when blank.
 * @param onDismiss Has to dismiss this sheet's own dialog and nothing else (`CampfireViewModel.dismissSheet`): it is
 *   called from the end of a hide animation, by which time another dialog may have taken the sheet's place.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CampfireBottomSheet(
    title: String,
    subtitle: String = "",
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.(contentPadding: PaddingValues) -> Unit,
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
        contentWindowInsets = { WindowInsets.safeDrawing.only(WindowInsetsSides.Top) },
    ) {
        SheetHeader(
            title = title,
            subtitle = subtitle,
            // Hiding the sheet by hand does not count as dismissing it, so the dialog state is cleared once it is
            // gone: left as it was, the invisible sheet's modal layer would stay over the screen, swallowing the next
            // tap. Only a hide that ran to its end counts: one cut short by a finger taking hold of the sheet leaves the
            // sheet where Material settles it, and one cut short by another dialog replacing the sheet has nothing left
            // to dismiss.
            onClose = { coroutineScope.launch { sheetState.hide() }.invokeOnCompletion { cause -> if (cause == null) onDismiss() } },
        )
        // Read inside the sheet, which is a window of its own on Android and gets the insets of that window.
        val bottomInset = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding()
        content(PaddingValues(bottom = bottomInset + SHEET_BOTTOM_PADDING))
    }
}

/**
 * The top of every sheet: what it is about, and a close button. A sheet whose list grows past the screen covers the
 * whole of it once it is dragged up, and a sheet that fills the screen has no scrim left to tap and no edge that looks
 * like it could be dragged back down, so the button is on the short sheets too, where the next one opened may not be
 * short. A picker sheet names its song and artist, or its setlist and description, here: it is opened from rows of
 * other lists as readily as from the thing itself, and without this it would never say what the boxes are about.
 */
@Composable
private fun SheetHeader(
    title: String,
    subtitle: String,
    onClose: () -> Unit,
) = Row(
    modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    IconButton(onClick = onClose) {
        Icon(
            painter = painterResource(Res.drawable.ic_clear),
            contentDescription = stringResource(Res.string.close),
        )
    }
    Column(
        modifier = Modifier.weight(1f),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // Neither an artist nor a description is required, and an empty line would only make the header taller.
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private val SHEET_BOTTOM_PADDING = 16.dp
private val PICKER_LIST_TOP_PADDING = 8.dp
private const val MAX_TITLE_LENGTH = 60
private const val MAX_TAG_LENGTH = 40
private const val MAX_DESCRIPTION_LENGTH = 300
private const val DESCRIPTION_LINES = 3
private val MAX_SUGGESTIONS_HEIGHT = 160.dp
private val MAX_LANGUAGES_HEIGHT = 320.dp

/** A field whose value becomes one line of a song file: a pasted line break is the space between two words. */
private fun String.asSingleLine() = replace(lineBreakRegex, " ")

private val lineBreakRegex = Regex("[\\r\\n]+")
