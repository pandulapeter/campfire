/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.messages

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarDuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.localization.pluralStringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.error_link_not_opened
import com.pandulapeter.campfire.presentation.resources.error_operation_failed
import com.pandulapeter.campfire.presentation.resources.export_failed
import com.pandulapeter.campfire.presentation.resources.export_library_saved
import com.pandulapeter.campfire.presentation.resources.export_log_saved
import com.pandulapeter.campfire.presentation.resources.export_pdf_saved
import com.pandulapeter.campfire.presentation.resources.export_setlist_saved
import com.pandulapeter.campfire.presentation.resources.export_skipped_files
import com.pandulapeter.campfire.presentation.resources.export_song_saved
import com.pandulapeter.campfire.presentation.resources.export_songs_saved
import com.pandulapeter.campfire.presentation.resources.export_too_large_to_import
import com.pandulapeter.campfire.metronome.api.model.MetronomeStopReason
import com.pandulapeter.campfire.data.model.share.SharingFailure
import com.pandulapeter.campfire.presentation.resources.metronome_stopped_disconnected
import com.pandulapeter.campfire.presentation.resources.metronome_stopped_failed
import com.pandulapeter.campfire.presentation.resources.metronome_stopped_interrupted
import com.pandulapeter.campfire.presentation.resources.metronome_stopped_refused
import com.pandulapeter.campfire.presentation.resources.metronome_stopped_silent
import com.pandulapeter.campfire.presentation.resources.import_failed
import com.pandulapeter.campfire.presentation.resources.import_interrupted
import com.pandulapeter.campfire.presentation.resources.import_converted
import com.pandulapeter.campfire.presentation.resources.import_details
import com.pandulapeter.campfire.presentation.resources.import_open
import com.pandulapeter.campfire.presentation.resources.import_status_stopped
import com.pandulapeter.campfire.presentation.resources.import_result
import com.pandulapeter.campfire.presentation.resources.onboarding_clipboard_link_open
import com.pandulapeter.campfire.presentation.resources.onboarding_clipboard_link_question
import com.pandulapeter.campfire.presentation.resources.paste_clipboard_empty
import com.pandulapeter.campfire.presentation.resources.paste_only_links
import com.pandulapeter.campfire.presentation.resources.paste_too_large
import com.pandulapeter.campfire.presentation.resources.shared_link_from_newer_version
import com.pandulapeter.campfire.presentation.resources.clipboard_not_campfire_link
import com.pandulapeter.campfire.presentation.resources.shared_link_not_imported
import com.pandulapeter.campfire.presentation.resources.share_authorization
import com.pandulapeter.campfire.presentation.resources.share_link_copied
import com.pandulapeter.campfire.presentation.resources.share_qr_code_saved
import com.pandulapeter.campfire.presentation.resources.share_scope_missing
import com.pandulapeter.campfire.presentation.resources.share_stop_all_failed
import com.pandulapeter.campfire.presentation.resources.share_stop_all_failed_network
import com.pandulapeter.campfire.presentation.resources.share_stop_failed
import com.pandulapeter.campfire.presentation.resources.share_stop_failed_network
import com.pandulapeter.campfire.presentation.resources.song_editor_draft_lost
import com.pandulapeter.campfire.presentation.resources.song_editor_draft_restored
import com.pandulapeter.campfire.presentation.resources.song_editor_file_gone
import com.pandulapeter.campfire.presentation.resources.song_editor_untitled
import com.pandulapeter.campfire.presentation.resources.songs_paste_chordpro
import com.pandulapeter.campfire.presentation.resources.songs_paste_converted
import com.pandulapeter.campfire.presentation.resources.songs_paste_no_chords
import com.pandulapeter.campfire.presentation.resources.songs_paste_notation_without_chords
import com.pandulapeter.campfire.presentation.resources.song_editor_save_failed
import com.pandulapeter.campfire.presentation.resources.songs_delete_song_partly
import com.pandulapeter.campfire.presentation.resources.songs_selection_failed
import com.pandulapeter.campfire.presentation.resources.songs_selection_updated
import com.pandulapeter.campfire.presentation.resources.songs_update_file_name_partly
import com.pandulapeter.campfire.presentation.resources.settings_sync_reconnect_failed
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.localization.pluralTextResource
import com.pandulapeter.campfire.presentation.localization.textResource

/**
 * The one line of text the app has to say after something it was asked to do has finished. It sits above the
 * navigation chrome rather than inside a screen, because the screen an import was started from is often not the one
 * the user is looking at when it ends.
 */
@Composable
internal fun Messages(
    modifier: Modifier = Modifier,
    viewModel: CampfireViewModel,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    // Only the head of the view model's queue is read: the text of a message can only be built in a composition
    // (string resources are composable), and two identical results in a row are still two messages - which is what
    // the numbering is for. Two failed exports are the same object, and an effect keyed on the message alone would not
    // restart for the second one: it would sit at the head of the queue forever, unshown, with everything after it
    // stuck behind it. A message that was on screen as the composition was recreated is shown again from the start,
    // since it was cut short.
    val queue by viewModel.messageSink.messageQueue.collectAsStateWithLifecycle()
    val head = queue.firstOrNull()
    val text = messageText(head?.value)
    val importFinished = head?.value as? Message.ImportFinished
    val areSongsEnabled by viewModel.preferencesController.areSongsEnabled.collectAsStateWithLifecycle()
    val songToOpen = importFinished?.result?.convertedSongToOpen?.takeIf { areSongsEnabled }
    val isClipboardLinkOffer = head?.value == Message.ClipboardLinkOffered
    val actionLabel = when {
        songToOpen != null -> stringResource(Res.string.import_open)
        importFinished?.hasDetails == true -> stringResource(Res.string.import_details)
        isClipboardLinkOffer -> stringResource(Res.string.onboarding_clipboard_link_open)
        else -> null
    }
    LaunchedEffect(head?.index) {
        if (head != null && text != null) {
            val result = snackbarHostState.showSnackbar(
                message = text,
                actionLabel = actionLabel,
                duration = if (actionLabel != null || '\n' in text) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                when {
                    songToOpen != null -> viewModel.navigator.openImportedSong(songToOpen)
                    importFinished != null -> viewModel.importController.openImportReport(importFinished.result)
                    isClipboardLinkOffer -> viewModel.firstRunController.openClipboardLink()
                }
            } else if (isClipboardLinkOffer) {
                // Let go, the offer is not kept for anything to read the clipboard later.
                viewModel.firstRunController.dropClipboardLinkOffer()
            }
            viewModel.messageSink.onMessageShown(head)
        }
    }
    SnackbarHost(
        modifier = modifier,
        hostState = snackbarHostState,
    ) { data ->
        Snackbar(snackbarData = data)
    }
}

/** What [current] says, or null for no message; built here because string resources can only be read in a composition. */
@Composable
private fun messageText(current: Message?): String? =
    when (current) {
        is Message.ImportFinished -> listOfNotNull(
            if (current.result.isFailed) stringResource(Res.string.import_status_stopped) else null,
            stringResource(
                Res.string.import_result,
                current.result.importedSongFileNames.size,
                current.result.importedSetlistFileNames.size,
                current.result.duplicateFileNames.size,
                current.result.skippedFileNames.size,
            ),
            if (current.result.convertedSongFileNames.isNotEmpty()) stringResource(Res.string.import_converted, current.result.convertedSongFileNames.size) else null,
        ).joinToString("\n")

        Message.ImportFailed -> stringResource(Res.string.import_failed)
        Message.ImportInterrupted -> stringResource(Res.string.import_interrupted)
        Message.ExportFailed -> stringResource(Res.string.export_failed)
        Message.PdfSaved -> stringResource(Res.string.export_pdf_saved)
        Message.SongExported -> stringResource(Res.string.export_song_saved)
        Message.SongsExported -> stringResource(Res.string.export_songs_saved)
        Message.SetlistExported -> stringResource(Res.string.export_setlist_saved)
        Message.LibraryExported -> stringResource(Res.string.export_library_saved)
        Message.ProcessLogSaved -> stringResource(Res.string.export_log_saved)
        Message.ExportTooLargeToImport -> stringResource(Res.string.export_too_large_to_import)
        is Message.ExportSkippedFiles -> pluralTextResource(
            Res.plurals.export_skipped_files,
            current.fileNames.size,
            current.fileNames.size.toString(),
            current.fileNames.take(MAXIMUM_NAMED_FILES).joinToString(),
        )

        Message.SaveFailed -> stringResource(Res.string.song_editor_save_failed)
        Message.EditorDraftLost -> stringResource(Res.string.song_editor_draft_lost)
        Message.EditorDraftRestored -> stringResource(Res.string.song_editor_draft_restored)
        Message.EditedSongFileGone -> stringResource(Res.string.song_editor_file_gone)
        Message.NewSongUntitled -> stringResource(Res.string.song_editor_untitled)
        // Lyrics alone may well come through the conversion unchanged, so the chords are asked about first.
        is Message.NewSongFromText -> stringResource(
            when {
                !current.hasChords && current.hasNotation -> Res.string.songs_paste_notation_without_chords
                !current.hasChords -> Res.string.songs_paste_no_chords
                current.isConverted -> Res.string.songs_paste_converted
                else -> Res.string.songs_paste_chordpro
            }
        )
        Message.OperationFailed -> stringResource(Res.string.error_operation_failed)
        is Message.SongsUpdated -> pluralStringResource(Res.plurals.songs_selection_updated, current.count, current.count)
        is Message.SongsNotUpdated -> pluralStringResource(Res.plurals.songs_selection_failed, current.count, current.count)
        Message.SongFileRenamedPartly -> stringResource(Res.string.songs_update_file_name_partly)
        Message.SongDeletedPartly -> stringResource(Res.string.songs_delete_song_partly)
        is Message.LinkNotOpened -> textResource(Res.string.error_link_not_opened, current.url)
        is Message.MetronomeStopped -> stringResource(
            when (current.reason) {
                MetronomeStopReason.AUDIO_REFUSED -> Res.string.metronome_stopped_refused
                MetronomeStopReason.AUDIO_INTERRUPTED -> Res.string.metronome_stopped_interrupted
                MetronomeStopReason.OUTPUT_DISCONNECTED -> Res.string.metronome_stopped_disconnected
                MetronomeStopReason.OUTPUT_FAILED -> Res.string.metronome_stopped_failed
            }
        )
        Message.SilentMetronomeStopped -> stringResource(Res.string.metronome_stopped_silent)
        Message.ClipboardEmpty -> stringResource(Res.string.paste_clipboard_empty)
        is Message.ClipboardRefused -> stringResource(current.text)
        Message.PastedOnlyLinks -> stringResource(Res.string.paste_only_links)
        Message.PastedTextTooLarge -> stringResource(Res.string.paste_too_large)
        Message.SharedLinkNotImported -> stringResource(Res.string.shared_link_not_imported)
        Message.SharedLinkFromNewerVersion -> stringResource(Res.string.shared_link_from_newer_version)
        Message.LinkCopied -> stringResource(Res.string.share_link_copied)
        Message.QrCodeSaved -> stringResource(Res.string.share_qr_code_saved)
        // A connection without the permission to make links, or one the service no longer takes, is told as Share tells it,
        // since connecting again is what lets a stop through too.
        is Message.StopSharingFailed -> stringResource(
            when (current.reason) {
                SharingFailure.NETWORK -> Res.string.share_stop_failed_network
                SharingFailure.SCOPE_MISSING -> Res.string.share_scope_missing
                SharingFailure.AUTHORIZATION -> Res.string.share_authorization
                else -> Res.string.share_stop_failed
            },
        )
        is Message.StopSharingEverythingFailed -> stringResource(
            when (current.reason) {
                SharingFailure.NETWORK -> Res.string.share_stop_all_failed_network
                SharingFailure.SCOPE_MISSING -> Res.string.share_scope_missing
                SharingFailure.AUTHORIZATION -> Res.string.share_authorization
                else -> Res.string.share_stop_all_failed
            },
        )
        Message.ClipboardLinkOffered -> stringResource(Res.string.onboarding_clipboard_link_question)
        Message.ClipboardNotCampfireLink -> stringResource(Res.string.clipboard_not_campfire_link)
        Message.ReconnectFailed -> stringResource(Res.string.settings_sync_reconnect_failed)
        null -> null
    }

/** How many of the files an export left out its message names, the rest being counted rather than listed. */
private const val MAXIMUM_NAMED_FILES = 3
