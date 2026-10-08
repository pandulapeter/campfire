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
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.error_link_not_opened
import com.pandulapeter.campfire.presentation.resources.error_operation_failed
import com.pandulapeter.campfire.presentation.resources.export_failed
import com.pandulapeter.campfire.presentation.resources.export_library_saved
import com.pandulapeter.campfire.presentation.resources.export_pdf_saved
import com.pandulapeter.campfire.presentation.resources.export_setlist_saved
import com.pandulapeter.campfire.presentation.resources.export_skipped_files
import com.pandulapeter.campfire.presentation.resources.export_song_saved
import com.pandulapeter.campfire.presentation.resources.export_too_large_to_import
import com.pandulapeter.campfire.metronome.api.model.MetronomeStopReason
import com.pandulapeter.campfire.presentation.resources.metronome_stopped_disconnected
import com.pandulapeter.campfire.presentation.resources.metronome_stopped_failed
import com.pandulapeter.campfire.presentation.resources.metronome_stopped_interrupted
import com.pandulapeter.campfire.presentation.resources.metronome_stopped_refused
import com.pandulapeter.campfire.presentation.resources.metronome_stopped_silent
import com.pandulapeter.campfire.presentation.resources.import_failed
import com.pandulapeter.campfire.presentation.resources.import_converted
import com.pandulapeter.campfire.presentation.resources.import_details
import com.pandulapeter.campfire.presentation.resources.import_open
import com.pandulapeter.campfire.presentation.resources.import_status_stopped
import com.pandulapeter.campfire.presentation.resources.import_result
import com.pandulapeter.campfire.presentation.resources.song_editor_draft_lost
import com.pandulapeter.campfire.presentation.resources.song_editor_draft_restored
import com.pandulapeter.campfire.presentation.resources.song_editor_file_gone
import com.pandulapeter.campfire.presentation.resources.song_editor_save_failed
import com.pandulapeter.campfire.presentation.resources.songs_delete_song_partly
import com.pandulapeter.campfire.presentation.resources.songs_update_file_name_partly
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.pluralTextResource
import com.pandulapeter.campfire.presentation.ui.components.textResource

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
    val queue by viewModel.messageQueue.collectAsStateWithLifecycle()
    val head = queue.firstOrNull()
    val text = when (val current = head?.value) {
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
        Message.ExportFailed -> stringResource(Res.string.export_failed)
        Message.PdfSaved -> stringResource(Res.string.export_pdf_saved)
        Message.SongExported -> stringResource(Res.string.export_song_saved)
        Message.SetlistExported -> stringResource(Res.string.export_setlist_saved)
        Message.LibraryExported -> stringResource(Res.string.export_library_saved)
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
        Message.OperationFailed -> stringResource(Res.string.error_operation_failed)
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
        null -> null
    }
    val importFinished = head?.value as? Message.ImportFinished
    val songToOpen = importFinished?.result?.convertedSongToOpen
    val actionLabel = when {
        songToOpen != null -> stringResource(Res.string.import_open)
        importFinished?.hasDetails == true -> stringResource(Res.string.import_details)
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
                    songToOpen != null -> viewModel.openImportedSong(songToOpen)
                    importFinished != null -> viewModel.openImportReport(importFinished.result)
                }
            }
            viewModel.onMessageShown(head)
        }
    }
    SnackbarHost(
        modifier = modifier,
        hostState = snackbarHostState,
    ) { data ->
        Snackbar(snackbarData = data)
    }
}

/** How many of the files an export left out its message names, the rest being counted rather than listed. */
private const val MAXIMUM_NAMED_FILES = 3
