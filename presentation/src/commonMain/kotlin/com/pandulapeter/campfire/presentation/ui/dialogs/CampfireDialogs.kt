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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.data.model.domain.ImportConflictResolution
import com.pandulapeter.campfire.presentation.localization.pluralStringResource
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.cover_art_search_remove
import com.pandulapeter.campfire.presentation.resources.song_details_remove_cover_art_confirmation
import com.pandulapeter.campfire.presentation.resources.close
import com.pandulapeter.campfire.presentation.resources.confirm_exit_message
import com.pandulapeter.campfire.presentation.resources.confirm_exit_title
import com.pandulapeter.campfire.presentation.resources.create
import com.pandulapeter.campfire.presentation.resources.delete
import com.pandulapeter.campfire.presentation.resources.import_conflicts_replace
import com.pandulapeter.campfire.presentation.resources.import_conflicts_replace_description
import com.pandulapeter.campfire.presentation.resources.import_replace_title
import com.pandulapeter.campfire.presentation.resources.save
import com.pandulapeter.campfire.presentation.resources.setlists_delete_setlist
import com.pandulapeter.campfire.presentation.resources.setlists_remove_song
import com.pandulapeter.campfire.presentation.resources.setlists_remove_song_confirmation
import com.pandulapeter.campfire.presentation.resources.setlists_delete_setlist_confirmation
import com.pandulapeter.campfire.presentation.resources.setlists_duplicate
import com.pandulapeter.campfire.presentation.resources.setlists_duplicate_setlist
import com.pandulapeter.campfire.presentation.resources.setlists_duplicate_title
import com.pandulapeter.campfire.presentation.resources.setlists_edit_details
import com.pandulapeter.campfire.presentation.resources.setlists_new_setlist
import com.pandulapeter.campfire.presentation.resources.settings_library_cover_art_cache_clear
import com.pandulapeter.campfire.presentation.resources.settings_library_cover_art_cache_clear_action
import com.pandulapeter.campfire.presentation.resources.settings_library_cover_art_cache_clear_confirmation
import com.pandulapeter.campfire.presentation.resources.settings_sync_disconnect
import com.pandulapeter.campfire.presentation.resources.settings_sync_disconnect_confirmation
import com.pandulapeter.campfire.presentation.resources.song_editor_revert
import com.pandulapeter.campfire.presentation.resources.song_editor_revert_confirmation
import com.pandulapeter.campfire.presentation.resources.songs_delete_song
import com.pandulapeter.campfire.presentation.resources.songs_delete_song_confirmation
import com.pandulapeter.campfire.presentation.resources.songs_filter
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.SongFilters
import com.pandulapeter.campfire.presentation.ui.components.textResource
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

/**
 * Hosts whichever dialog or bottom sheet the view model asks for.
 */
@Composable
internal fun CampfireDialogs(
    viewModel: CampfireViewModel,
    urlOpener: (String) -> Unit,
) {
    val visibleDialog by viewModel.visibleDialog.collectAsStateWithLifecycle()
    val underlyingSongInfo by viewModel.underlyingSongInfo.collectAsStateWithLifecycle()
    val importProgress by viewModel.importProgress.collectAsStateWithLifecycle()
    val importReport by viewModel.importReport.collectAsStateWithLifecycle()
    // The import screen shows the progress of the imports it reports on itself.
    ImportProgressDialogHost(
        progress = importProgress.takeIf { importReport == null },
        canShow = visibleDialog == null,
        onCancel = viewModel::cancelImportPreparation,
    )
    // A stable composition slot keeps the parent sheet mounted while its editor opens in another modal window.
    val songInfo = underlyingSongInfo ?: (visibleDialog as? CampfireViewModel.DialogType.SongInfo)
    if (songInfo != null) {
        SongInfoSheet(viewModel = viewModel, dialog = songInfo, urlOpener = urlOpener)
    }
    when (val dialog = visibleDialog) {
        // Drawn by ExportHost, which deals it over the screens rather than in a window of its own.
        is CampfireViewModel.DialogType.Export -> Unit
        CampfireViewModel.DialogType.NewSetlist -> SetlistDetailsDialog(
            title = stringResource(Res.string.setlists_new_setlist),
            confirmLabel = stringResource(Res.string.create),
            onDismiss = { viewModel.dismissSheet(dialog) },
            // Dismissed before the setlist is created rather than after, since creating it is what opens the song
            // picker for it, and a dismissal arriving after that would close the picker instead.
            onConfirm = { setlistTitle, description, date, isCountdownShown ->
                viewModel.dismissDialog()
                viewModel.createSetlist(title = setlistTitle, description = description, date = date, isCountdownShown = isCountdownShown)
            },
        )

        is CampfireViewModel.DialogType.EditSetlist -> SetlistDetailsDialog(
            title = stringResource(Res.string.setlists_edit_details),
            subtitle = dialog.setlist.title,
            isTitleFocused = false,
            requiresChanges = true,
            initialTitle = dialog.setlist.title,
            initialDescription = dialog.setlist.description,
            initialDate = dialog.setlist.date,
            initialIsCountdownShown = dialog.setlist.isCountdownShown,
            confirmLabel = stringResource(Res.string.save),
            onDismiss = { viewModel.dismissSheet(dialog) },
            onConfirm = { setlistTitle, description, date, isCountdownShown ->
                viewModel.editSetlist(
                    offered = dialog.setlist,
                    title = setlistTitle,
                    description = description,
                    date = date,
                    isCountdownShown = isCountdownShown,
                )
            },
        )

        // Named before it is made rather than after: two setlists can carry the same title (a setlist is identified
        // by its file name), so a copy nobody named would sit under the original's title until somebody noticed.
        is CampfireViewModel.DialogType.DuplicateSetlist -> SetlistDetailsDialog(
            title = stringResource(Res.string.setlists_duplicate_setlist),
            subtitle = dialog.setlist.title,
            initialTitle = textResource(Res.string.setlists_duplicate_title, dialog.setlist.title),
            initialDescription = dialog.setlist.description,
            initialIsCountdownShown = dialog.setlist.isCountdownShown,
            confirmLabel = stringResource(Res.string.setlists_duplicate),
            onDismiss = { viewModel.dismissSheet(dialog) },
            onConfirm = { setlistTitle, description, date, isCountdownShown ->
                viewModel.duplicateSetlist(
                    setlist = dialog.setlist,
                    title = setlistTitle,
                    description = description,
                    date = date,
                    isCountdownShown = isCountdownShown,
                )
            },
        )

        CampfireViewModel.DialogType.NewSong -> NewSongDialog(
            onDismiss = { viewModel.dismissSheet(dialog) },
            onCreate = { values -> viewModel.createSong(values = values) },
        )

        CampfireViewModel.DialogType.SongFilters -> CampfireBottomSheet(
            title = stringResource(Res.string.songs_filter),
            onDismiss = { viewModel.dismissSheet(CampfireViewModel.DialogType.SongFilters) },
        ) { contentPadding ->
            SongFilters(
                viewModel = viewModel,
                contentPadding = contentPadding,
                uncoveredTopInset = uncoveredTopInset,
                fadeBackgroundColor = sheetContainerColor(),
            )
        }

        is CampfireViewModel.DialogType.SongInfo -> Unit
        is CampfireViewModel.DialogType.ChordShapes -> ChordShapesSheet(viewModel = viewModel, dialog = dialog)

        is CampfireViewModel.DialogType.SetlistPicker -> SetlistPicker(
            viewModel = viewModel,
            dialog = dialog,
        )

        is CampfireViewModel.DialogType.SongPicker -> SongPicker(
            viewModel = viewModel,
            dialog = dialog,
        )

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

        is CampfireViewModel.DialogType.SongTags -> SongTagsDialog(
            viewModel = viewModel,
            dialog = dialog,
        )

        is CampfireViewModel.DialogType.SongMetadata -> SongMetadataDialog(
            viewModel = viewModel,
            dialog = dialog,
        )

        is CampfireViewModel.DialogType.SongLinks -> SongLinksDialog(
            viewModel = viewModel,
            dialog = dialog,
        )

        is CampfireViewModel.DialogType.SongLanguages -> SongLanguagesDialog(
            viewModel = viewModel,
            dialog = dialog,
        )

        is CampfireViewModel.DialogType.SongPlaying -> SongPlayingDialog(
            viewModel = viewModel,
            dialog = dialog,
        )

        is CampfireViewModel.DialogType.CoverArtSearch -> CoverArtSearchSheet(
            viewModel = viewModel,
            dialog = dialog,
        )

        is CampfireViewModel.DialogType.RemoveSongCoverArt -> ConfirmationDialog(
            title = stringResource(Res.string.cover_art_search_remove),
            text = textResource(Res.string.song_details_remove_cover_art_confirmation, dialog.song.title),
            confirmLabel = stringResource(Res.string.cover_art_search_remove),
            // Not removing the cover is not being done with it: the sheet the bin was tapped in comes back.
            onDismiss = { viewModel.showDialog(CampfireViewModel.DialogType.CoverArtSearch(song = dialog.song, isEditorDraft = dialog.isEditorDraft)) },
            onConfirm = {
                viewModel.setSongCoverArt(fileName = dialog.song.fileName, isEditorDraft = dialog.isEditorDraft, url = null)
                viewModel.dismissDialog()
            },
        )

        is CampfireViewModel.DialogType.RemoveSongFromSetlist -> ConfirmationDialog(
            title = stringResource(Res.string.setlists_remove_song),
            text = textResource(Res.string.setlists_remove_song_confirmation, dialog.songTitle),
            confirmLabel = stringResource(Res.string.setlists_remove_song),
            onDismiss = viewModel::dismissDialog,
            onConfirm = {
                viewModel.removeSongFromSetlist(songFileName = dialog.songFileName, setlistFileName = dialog.setlistFileName)
                viewModel.dismissDialog()
            },
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

        CampfireViewModel.DialogType.ClearCoverArtCache -> ConfirmationDialog(
            title = stringResource(Res.string.settings_library_cover_art_cache_clear),
            text = stringResource(Res.string.settings_library_cover_art_cache_clear_confirmation),
            confirmLabel = stringResource(Res.string.settings_library_cover_art_cache_clear_action),
            onDismiss = viewModel::dismissDialog,
            onConfirm = {
                viewModel.clearCoverArtCache()
                viewModel.dismissDialog()
            },
        )

        CampfireViewModel.DialogType.DeleteLibrary -> DeleteLibraryDialog(
            viewModel = viewModel,
        )

        CampfireViewModel.DialogType.RevertChanges -> ConfirmationDialog(
            title = stringResource(Res.string.song_editor_revert),
            text = stringResource(Res.string.song_editor_revert_confirmation),
            confirmLabel = stringResource(Res.string.song_editor_revert),
            onDismiss = viewModel::dismissDialog,
            onConfirm = viewModel::revertEditorChanges,
        )

        is CampfireViewModel.DialogType.ConfirmImportReplace -> ConfirmationDialog(
            title = pluralStringResource(Res.plurals.import_replace_title, dialog.count, dialog.count),
            text = stringResource(Res.string.import_conflicts_replace_description),
            confirmLabel = stringResource(Res.string.import_conflicts_replace),
            onDismiss = viewModel::dismissDialog,
            onConfirm = {
                viewModel.dismissDialog()
                viewModel.resolveImport(ImportConflictResolution.REPLACE)
            },
        )

        CampfireViewModel.DialogType.ConfirmExit -> ConfirmationDialog(
            title = stringResource(Res.string.confirm_exit_title),
            text = stringResource(Res.string.confirm_exit_message),
            confirmLabel = stringResource(Res.string.close),
            isDestructive = false,
            onDismiss = viewModel::dismissDialog,
            onConfirm = viewModel::exitConfirmed,
        )

        CampfireViewModel.DialogType.UnsavedChanges -> UnsavedChangesDialog(
            isSaving = viewModel.isSavingSong.collectAsStateWithLifecycle().value,
            onCancel = viewModel::dismissDialog,
            onDiscard = viewModel::leaveEditorWithoutSaving,
            onSave = viewModel::saveEditorChangesAndLeave,
        )

        CampfireViewModel.DialogType.Welcome -> WelcomeDialog(
            viewModel = viewModel,
        )

        CampfireViewModel.DialogType.WhatsNew -> {
            // Only once it has stayed up for a moment: Play's answer can arrive after the app is on screen and cover the
            // dialog with the update required screen, which takes it out of the composition and cancels this, and whose
            // flow ends the process (see CampfireViewModel.showWhatsNewOnVersionChange).
            LaunchedEffect(Unit) {
                delay(WHATS_NEW_SEEN_DELAY)
                viewModel.onWhatsNewShown()
            }
            WhatsNewDialog(onDismiss = viewModel::dismissDialog)
        }

        null -> Unit
    }
}

/** How long What's new has to stay uncovered before it counts as seen. */
private val WHATS_NEW_SEEN_DELAY = 3.seconds
