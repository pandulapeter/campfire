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

import com.pandulapeter.campfire.presentation.ui.songInfo.SongInfoSheet
import com.pandulapeter.campfire.presentation.ui.dialogs.about.OpenSourceLicenseSheet
import com.pandulapeter.campfire.presentation.ui.dialogs.about.OpenSourceLicensesSheet
import com.pandulapeter.campfire.presentation.ui.dialogs.about.WhatsNewDialog
import com.pandulapeter.campfire.presentation.ui.dialogs.instrument.ChordReferenceSheet
import com.pandulapeter.campfire.presentation.ui.dialogs.scales.ScaleSheet
import com.pandulapeter.campfire.presentation.ui.dialogs.instrument.ChordShapeSheet
import com.pandulapeter.campfire.presentation.ui.dialogs.instrument.InstrumentSheet
import com.pandulapeter.campfire.presentation.ui.dialogs.instrument.NotationSheet
import com.pandulapeter.campfire.presentation.ui.dialogs.songDisplay.SongDisplaySheet
import com.pandulapeter.campfire.presentation.ui.dialogs.songDisplay.SongLayoutSheet
import com.pandulapeter.campfire.presentation.ui.dialogs.instrument.TunerSheet
import com.pandulapeter.campfire.presentation.ui.dialogs.instrument.TuningSheet
import com.pandulapeter.campfire.presentation.ui.dialogs.lookup.CoverArtSearchSheet
import com.pandulapeter.campfire.presentation.ui.dialogs.lookup.SongDetailsLookupSheet
import com.pandulapeter.campfire.presentation.ui.dialogs.pickers.AppLanguageSheet
import com.pandulapeter.campfire.presentation.ui.dialogs.pickers.SelectionSetlistsSheet
import com.pandulapeter.campfire.presentation.ui.dialogs.pickers.SetlistPicker
import com.pandulapeter.campfire.presentation.ui.dialogs.pickers.SongPicker
import com.pandulapeter.campfire.presentation.ui.dialogs.labels.SelectionLanguagesSheet
import com.pandulapeter.campfire.presentation.ui.dialogs.labels.ImportTagsSheet
import com.pandulapeter.campfire.presentation.ui.dialogs.labels.SelectionTagsSheet
import com.pandulapeter.campfire.presentation.ui.dialogs.labels.SongLanguagesDialog
import com.pandulapeter.campfire.presentation.ui.dialogs.labels.SongTagsDialog
import com.pandulapeter.campfire.presentation.ui.dialogs.forms.NewSongDialog
import com.pandulapeter.campfire.presentation.ui.dialogs.forms.SetlistDatePickerSheet
import com.pandulapeter.campfire.presentation.ui.dialogs.forms.SetlistDetailsDialog
import com.pandulapeter.campfire.presentation.ui.dialogs.forms.SongLinksDialog
import com.pandulapeter.campfire.presentation.ui.dialogs.forms.SongMetadataDialog
import com.pandulapeter.campfire.presentation.ui.dialogs.forms.SongPlayingDialog
import com.pandulapeter.campfire.presentation.ui.dialogs.sheet.LocalIsSheetCloseRequested
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.presentation.localization.pluralStringResource
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.cover_art_search_remove
import com.pandulapeter.campfire.presentation.resources.song_details_remove_cover_art_confirmation
import com.pandulapeter.campfire.presentation.resources.close
import com.pandulapeter.campfire.presentation.resources.confirm_exit_message
import com.pandulapeter.campfire.presentation.resources.confirm_exit_title
import com.pandulapeter.campfire.presentation.resources.create
import com.pandulapeter.campfire.presentation.resources.import_conflicts_replace
import com.pandulapeter.campfire.presentation.resources.import_replace_message
import com.pandulapeter.campfire.presentation.resources.import_replace_title
import com.pandulapeter.campfire.presentation.resources.save
import com.pandulapeter.campfire.presentation.resources.setlists_remove_song
import com.pandulapeter.campfire.presentation.resources.setlists_remove_song_confirmation
import com.pandulapeter.campfire.presentation.resources.duplicate
import com.pandulapeter.campfire.presentation.resources.setlists_duplicate_setlist
import com.pandulapeter.campfire.presentation.resources.setlists_duplicate_title
import com.pandulapeter.campfire.presentation.resources.setlists_edit_details
import com.pandulapeter.campfire.presentation.resources.setlists_new_setlist
import com.pandulapeter.campfire.presentation.resources.settings_library_cover_art_cache_clear
import com.pandulapeter.campfire.presentation.resources.settings_library_cover_art_cache_clear_action
import com.pandulapeter.campfire.presentation.resources.settings_library_cover_art_cache_clear_confirmation
import com.pandulapeter.campfire.presentation.resources.settings_sync_disconnect
import com.pandulapeter.campfire.presentation.resources.settings_sync_disconnect_confirmation
import com.pandulapeter.campfire.presentation.resources.settings_sync_disconnect_pending_stops
import com.pandulapeter.campfire.presentation.resources.settings_sync_disconnect_shared_links
import com.pandulapeter.campfire.presentation.resources.song_editor_revert
import com.pandulapeter.campfire.presentation.resources.song_editor_revert_confirmation
import com.pandulapeter.campfire.presentation.resources.songs_duplicate_song
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.localization.textResource
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
    val openDialogs by viewModel.dialogHost.openDialogs.collectAsStateWithLifecycle()
    val closeRequest by viewModel.dialogHost.closeRequest.collectAsStateWithLifecycle()
    // A slot of its own for every open dialog, keyed by its place, so that one a dialog was put over stays composed
    // under it - its sheet up, its draft and scroll position kept - and is only uncovered when that one goes.
    openDialogs.forEach { dialog ->
        key(dialog.slotKey) {
            CompositionLocalProvider(LocalIsSheetCloseRequested provides (dialog == closeRequest)) {
                CampfireDialog(viewModel = viewModel, dialog = dialog, urlOpener = urlOpener)
            }
        }
    }
}

/** One open dialog of [CampfireDialogs], whichever place in the stack it has. */
@Composable
private fun CampfireDialog(
    viewModel: CampfireViewModel,
    dialog: DialogType,
    urlOpener: (String) -> Unit,
) {
    when (dialog) {
        // Drawn by ExportHost, which deals it over the screens rather than in a window of its own.
        is DialogType.Export -> Unit
        DialogType.NewSetlist -> SetlistDetailsDialog(
            title = stringResource(Res.string.setlists_new_setlist),
            confirmLabel = stringResource(Res.string.create),
            onDismiss = { viewModel.dialogHost.dismissSheet(dialog) },
            // Dismissed before the setlist is created rather than after, since creating it is what opens the song
            // picker for it, and a dismissal arriving after that would close the picker instead.
            onConfirm = { setlistTitle, description, date, isCountdownShown ->
                viewModel.dialogHost.dismissDialog()
                viewModel.setlistsController.createSetlist(
                    title = setlistTitle,
                    description = description,
                    date = date,
                    isCountdownShown = isCountdownShown,
                )
            },
        )

        is DialogType.EditSetlist -> SetlistDetailsDialog(
            title = stringResource(Res.string.setlists_edit_details),
            subtitle = dialog.setlist.title,
            isTitleFocused = false,
            requiresChanges = true,
            initialTitle = dialog.setlist.title,
            initialDescription = dialog.setlist.description,
            initialDate = dialog.setlist.date,
            initialIsCountdownShown = dialog.setlist.isCountdownShown,
            confirmLabel = stringResource(Res.string.save),
            onDismiss = { viewModel.dialogHost.dismissSheet(dialog) },
            onConfirm = { setlistTitle, description, date, isCountdownShown ->
                viewModel.setlistsController.editSetlist(
                    offered = dialog.setlist,
                    title = setlistTitle,
                    description = description,
                    date = date,
                    isCountdownShown = isCountdownShown,
                )
            },
        )

        is DialogType.SetlistDate -> SetlistDatePickerSheet(
            date = dialog.setlist.date,
            // The other details are the ones offered, so the edit writes the day and nothing else.
            onDateChange = { date ->
                viewModel.setlistsController.editSetlist(
                    offered = dialog.setlist,
                    title = dialog.setlist.title,
                    description = dialog.setlist.description,
                    date = date,
                    isCountdownShown = dialog.setlist.isCountdownShown,
                )
            },
            onDismiss = { viewModel.dialogHost.dismissSheet(dialog) },
        )

        // Named before it is made rather than after: two setlists can carry the same title (a setlist is identified
        // by its file name), so a copy nobody named would sit under the original's title until somebody noticed.
        is DialogType.DuplicateSetlist -> SetlistDetailsDialog(
            title = stringResource(Res.string.setlists_duplicate_setlist),
            subtitle = dialog.setlist.title,
            initialTitle = textResource(Res.string.setlists_duplicate_title, dialog.setlist.title),
            initialDescription = dialog.setlist.description,
            initialIsCountdownShown = dialog.setlist.isCountdownShown,
            confirmLabel = stringResource(Res.string.duplicate),
            onDismiss = { viewModel.dialogHost.dismissSheet(dialog) },
            onConfirm = { setlistTitle, description, date, isCountdownShown ->
                viewModel.setlistsController.duplicateSetlist(
                    setlist = dialog.setlist,
                    title = setlistTitle,
                    description = description,
                    date = date,
                    isCountdownShown = isCountdownShown,
                )
            },
        )

        DialogType.NewSong -> NewSongDialog(
            onDismiss = { viewModel.dialogHost.dismissSheet(dialog) },
            onCreate = { values, _ -> viewModel.songFilesController.createSong(values = values) },
        )

        is DialogType.DuplicateSong -> NewSongDialog(
            title = stringResource(Res.string.songs_duplicate_song),
            subtitle = songLabel(dialog.song),
            songFileName = dialog.song.fileName,
            offeredValues = dialog.values,
            confirmLabel = stringResource(Res.string.duplicate),
            onDismiss = { viewModel.dialogHost.dismissSheet(dialog) },
            onCreate = { values, offeredValues ->
                viewModel.songFilesController.duplicateSong(song = dialog.song, values = values, offeredValues = offeredValues)
            },
        )

        DialogType.SongFilters -> SongFiltersSheet(viewModel = viewModel)

        is DialogType.SongInfo -> SongInfoSheet(viewModel = viewModel, dialog = dialog, urlOpener = urlOpener)
        is DialogType.ChordShape -> ChordShapeSheet(viewModel = viewModel, dialog = dialog)
        is DialogType.ChordReference -> ChordReferenceSheet(viewModel = viewModel, dialog = dialog)
        is DialogType.Scale -> ScaleSheet(viewModel = viewModel, dialog = dialog)
        DialogType.Instrument -> InstrumentSheet(viewModel = viewModel, dialog = DialogType.Instrument)
        DialogType.Tuning -> TuningSheet(viewModel = viewModel, dialog = DialogType.Tuning)
        DialogType.Notation -> NotationSheet(viewModel = viewModel)
        DialogType.SongDisplay -> SongDisplaySheet(viewModel = viewModel)
        DialogType.SongLayout -> SongLayoutSheet(viewModel = viewModel)
        DialogType.Tuner -> TunerSheet(viewModel = viewModel, dialog = DialogType.Tuner)

        DialogType.AppLanguage -> AppLanguageSheet(viewModel = viewModel)
        DialogType.OpenSourceLicenses -> OpenSourceLicensesSheet(viewModel = viewModel)
        is DialogType.OpenSourceLicense -> OpenSourceLicenseSheet(viewModel = viewModel, dialog = dialog, urlOpener = urlOpener)

        is DialogType.SetlistPicker -> SetlistPicker(
            viewModel = viewModel,
            dialog = dialog,
        )

        is DialogType.SelectionSetlists -> SelectionSetlistsSheet(
            viewModel = viewModel,
            dialog = dialog,
        )

        is DialogType.SelectionTags -> SelectionTagsSheet(
            viewModel = viewModel,
            dialog = dialog,
        )

        is DialogType.SelectionLanguages -> SelectionLanguagesSheet(
            viewModel = viewModel,
            dialog = dialog,
        )

        is DialogType.SongPicker -> SongPicker(
            viewModel = viewModel,
            dialog = dialog,
        )

        is DialogType.DeleteSong -> DeleteSongConfirmation(
            viewModel = viewModel,
            dialog = dialog,
        )

        is DialogType.SongTags -> SongTagsDialog(
            viewModel = viewModel,
            dialog = dialog,
        )

        is DialogType.SongMetadata -> SongMetadataDialog(
            viewModel = viewModel,
            dialog = dialog,
        )

        is DialogType.SongDetailsLookup -> SongDetailsLookupSheet(
            viewModel = viewModel,
            dialog = dialog,
        )

        is DialogType.SongLinks -> SongLinksDialog(
            viewModel = viewModel,
            dialog = dialog,
        )

        is DialogType.SongLanguages -> SongLanguagesDialog(
            viewModel = viewModel,
            dialog = dialog,
        )

        is DialogType.SongPlaying -> SongPlayingDialog(
            viewModel = viewModel,
            dialog = dialog,
        )

        is DialogType.CoverArtSearch -> CoverArtSearchSheet(
            viewModel = viewModel,
            dialog = dialog,
        )

        is DialogType.RemoveSongCoverArt -> ConfirmationDialog(
            title = stringResource(Res.string.cover_art_search_remove),
            text = textResource(Res.string.song_details_remove_cover_art_confirmation, dialog.song.title),
            confirmLabel = stringResource(Res.string.cover_art_search_remove),
            // Not removing the cover is not being done with it: the cover sheet the bin was tapped in is still under it.
            onDismiss = { viewModel.dialogHost.dismissDialog() },
            onConfirm = {
                viewModel.songMetadataEditing.setSongCoverArt(target = dialog.target, url = null)
                // The cover sheet under the question goes with it, sliding away: there is nothing left for it to change.
                viewModel.dialogHost.dismissDialogAndSheetUnder()
            },
        )

        is DialogType.RemoveSongFromSetlist -> ConfirmationDialog(
            title = stringResource(Res.string.setlists_remove_song),
            text = textResource(Res.string.setlists_remove_song_confirmation, dialog.songTitle),
            confirmLabel = stringResource(Res.string.setlists_remove_song),
            onDismiss = viewModel.dialogHost::dismissDialog,
            onConfirm = {
                viewModel.setlistsController.removeSongFromSetlist(songFileName = dialog.songFileName, setlistFileName = dialog.setlistFileName)
                viewModel.dialogHost.dismissDialog()
            },
        )

        is DialogType.DeleteSetlist -> DeleteSetlistConfirmation(
            viewModel = viewModel,
            dialog = dialog,
        )

        is DialogType.DisconnectSync -> ConfirmationDialog(
            title = stringResource(Res.string.settings_sync_disconnect),
            // The shares stay in the cloud folder and keep serving their last copies, which nothing updates any more.
            text = textResource(Res.string.settings_sync_disconnect_confirmation, dialog.accountName) +
                if (viewModel.shareLinkController.shares.collectAsStateWithLifecycle().value.isEmpty()) {
                    ""
                } else {
                    " " + stringResource(Res.string.settings_sync_disconnect_shared_links)
                } +
                // Deletions not yet told to the account's list are forgotten with it, leaving any of their links working.
                if (viewModel.shareLinkController.hasPendingStops.collectAsStateWithLifecycle().value) {
                    " " + stringResource(Res.string.settings_sync_disconnect_pending_stops)
                } else {
                    ""
                },
            confirmLabel = stringResource(Res.string.settings_sync_disconnect),
            onDismiss = viewModel.dialogHost::dismissDialog,
            onConfirm = {
                viewModel.syncController.disconnectSyncProvider()
                viewModel.dialogHost.dismissDialog()
            },
        )

        DialogType.ClearCoverArtCache -> ConfirmationDialog(
            title = stringResource(Res.string.settings_library_cover_art_cache_clear),
            text = stringResource(Res.string.settings_library_cover_art_cache_clear_confirmation),
            confirmLabel = stringResource(Res.string.settings_library_cover_art_cache_clear_action),
            onDismiss = viewModel.dialogHost::dismissDialog,
            onConfirm = {
                viewModel.coverArtSearchController.clearCoverArtCache()
                viewModel.dialogHost.dismissDialog()
            },
        )

        is DialogType.SendProcessLog -> SendProcessLogDialog(
            isAfterCrash = dialog.isAfterCrash,
            onSend = { filePicker ->
                viewModel.exportController.sendProcessLog(filePicker)
                viewModel.dialogHost.dismissDialog()
            },
            onDismiss = viewModel.dialogHost::dismissDialog,
        )

        DialogType.DeleteLibrary -> DeleteLibraryDialog(
            viewModel = viewModel,
        )

        DialogType.RevertChanges -> ConfirmationDialog(
            title = stringResource(Res.string.song_editor_revert),
            text = stringResource(Res.string.song_editor_revert_confirmation),
            confirmLabel = stringResource(Res.string.song_editor_revert),
            onDismiss = viewModel.dialogHost::dismissDialog,
            onConfirm = viewModel.editorSession::revertEditorChanges,
        )

        is DialogType.ConfirmImportReplace -> ConfirmationDialog(
            title = pluralStringResource(Res.plurals.import_replace_title, dialog.count, dialog.count),
            text = pluralStringResource(Res.plurals.import_replace_message, dialog.count),
            confirmLabel = stringResource(Res.string.import_conflicts_replace),
            onDismiss = viewModel.dialogHost::dismissDialog,
            onConfirm = {
                // Resolved first: a batch waiting for this dialog to close can resume inside dismissDialog, and would
                // join the review it confirms unless the import has already taken it.
                viewModel.importController.resolveImport()
                viewModel.dialogHost.dismissDialog()
            },
        )

        is DialogType.ImportTags -> ImportTagsSheet(
            viewModel = viewModel,
            dialog = dialog,
        )

        DialogType.ConfirmExit -> ConfirmationDialog(
            title = stringResource(Res.string.confirm_exit_title),
            text = stringResource(Res.string.confirm_exit_message),
            confirmLabel = stringResource(Res.string.close),
            isDestructive = false,
            onDismiss = viewModel.dialogHost::dismissDialog,
            onConfirm = viewModel.appExitController::exitConfirmed,
        )

        DialogType.UnsavedChanges -> UnsavedChangesDialog(
            isSaving = viewModel.editorSession.isSavingSong.collectAsStateWithLifecycle().value,
            onCancel = viewModel.dialogHost::dismissDialog,
            onDiscard = viewModel.editorSession::leaveEditorWithoutSaving,
            onSave = viewModel.editorSession::saveEditorChangesAndLeave,
        )

        DialogType.WhatsNew -> {
            // Only once it has stayed up for a moment: Play's answer can arrive after the app is on screen and cover the
            // dialog with the update required screen, which takes it out of the composition and cancels this, and whose
            // flow ends the process (see FirstRunController.showWhatsNewOnVersionChange).
            LaunchedEffect(Unit) {
                delay(WHATS_NEW_SEEN_DELAY)
                viewModel.firstRunController.onWhatsNewShown()
            }
            WhatsNewDialog(onDismiss = viewModel.dialogHost::dismissDialog)
        }
    }
}

/** How long What's new has to stay uncovered before it counts as seen. */
private val WHATS_NEW_SEEN_DELAY = 3.seconds
