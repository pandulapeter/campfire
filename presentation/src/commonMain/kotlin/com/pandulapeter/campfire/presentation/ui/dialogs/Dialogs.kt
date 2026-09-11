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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.pandulapeter.campfire.presentation.resources.done
import com.pandulapeter.campfire.presentation.resources.ic_add
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
import com.pandulapeter.campfire.presentation.resources.setlists_delete_setlist
import com.pandulapeter.campfire.presentation.resources.setlists_delete_setlist_confirmation
import com.pandulapeter.campfire.presentation.resources.setlists_new_setlist
import com.pandulapeter.campfire.presentation.resources.setlists_new_setlist_title
import com.pandulapeter.campfire.presentation.resources.setlists_rename
import com.pandulapeter.campfire.presentation.resources.setlists_rename_title
import com.pandulapeter.campfire.presentation.resources.settings_sync_disconnect
import com.pandulapeter.campfire.presentation.resources.settings_sync_disconnect_confirmation
import com.pandulapeter.campfire.presentation.resources.song_details_add_to_setlist
import com.pandulapeter.campfire.presentation.resources.song_details_language
import com.pandulapeter.campfire.presentation.resources.song_details_language_no_search_results
import com.pandulapeter.campfire.presentation.resources.song_details_language_search
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
import com.pandulapeter.campfire.data.model.domain.ImportConflictResolution
import com.pandulapeter.campfire.data.model.domain.ImportPlan
import com.pandulapeter.campfire.data.model.domain.SongLanguage
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.ActionListItem
import com.pandulapeter.campfire.presentation.ui.components.CheckboxListItem
import com.pandulapeter.campfire.presentation.ui.components.PickableLanguage
import com.pandulapeter.campfire.presentation.ui.components.RadioListItem
import com.pandulapeter.campfire.presentation.ui.components.SettingsSectionTitle
import com.pandulapeter.campfire.presentation.ui.components.SongActions
import com.pandulapeter.campfire.presentation.ui.components.SongsControls
import com.pandulapeter.campfire.presentation.ui.components.TagFlowRow
import com.pandulapeter.campfire.presentation.ui.components.TagPill
import com.pandulapeter.campfire.presentation.ui.components.languageName
import com.pandulapeter.campfire.presentation.ui.components.pickableLanguages
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.SongDisplayControls
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import com.pandulapeter.campfire.presentation.localization.currentLanguage
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

        is CampfireViewModel.DialogType.SongLanguages -> SongLanguagesDialog(
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

        is CampfireViewModel.DialogType.ImportConflicts -> ImportConflictsDialog(
            summary = dialog.summary,
            onCancel = viewModel::cancelImport,
            onConfirm = viewModel::resolveImport,
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
                Text(stringResource(Res.string.import_conflicts_summary, summary.conflictingFileNames.size))
                Spacer(modifier = Modifier.height(8.dp))
                ImportConflictsFileNames(fileNames = summary.conflictingFileNames)
                if (summary.newSongCount > 0 || summary.newSetlistCount > 0) {
                    ImportConflictsNote(stringResource(Res.string.import_conflicts_new, summary.newSongCount, summary.newSetlistCount))
                }
                if (summary.duplicateCount > 0) {
                    ImportConflictsNote(stringResource(Res.string.import_conflicts_duplicates, summary.duplicateCount))
                }
                if (summary.skippedCount > 0) {
                    ImportConflictsNote(stringResource(Res.string.import_conflicts_skipped, summary.skippedCount))
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
    var selectedCodes by remember(dialog.song.fileName) { mutableStateOf(dialog.song.languages.toSet()) }
    val languages = remember(dialog.song, libraryLanguages, appLanguageCode) {
        val declared = dialog.song.languages
        val alsoOffer = declared + libraryLanguages.map { it.code }.filterNot { it == SongLanguage.UNKNOWN }
        val pickable = pickableLanguages(appLanguageCode = appLanguageCode, alsoOffer = alsoOffer, normalize = viewModel::normalize)
        // The song's own languages come first and stay there, in the order the file lists them.
        declared.mapNotNull { code -> pickable.firstOrNull { it.code == code } } + pickable.filterNot { it.code in declared }
    }
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
                    modifier = Modifier.fillMaxWidth(),
                    value = query,
                    onValueChange = { query = it.replace("\n", "") },
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
private val MAX_LANGUAGES_HEIGHT = 320.dp
