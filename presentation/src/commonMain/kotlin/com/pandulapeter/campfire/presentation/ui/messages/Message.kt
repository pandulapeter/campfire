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

import com.pandulapeter.campfire.data.model.domain.ImportResult
import com.pandulapeter.campfire.metronome.api.model.MetronomeStopReason
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel

/** Something that has happened and is worth one line of text at the bottom of the screen. */
sealed interface Message {
    /**
     * @param hasDetails Whether the snackbar offers the import screen, for an outcome that needed none but has
     *   more to it than the counts fit into one line: a batch of more than one file, or one whose screen was left
     *   while it was being written.
     */
    data class ImportFinished(val result: ImportResult, val hasDetails: Boolean) : Message

    data object ImportFailed : Message
    data object ExportFailed : Message
    data object PdfSaved : Message
    data object SongExported : Message
    data object SetlistExported : Message
    data object LibraryExported : Message

    /** An archive that was saved, but that the import would refuse for its size. */
    data object ExportTooLargeToImport : Message

    /** An archive that was saved without the files it names: they could not be read, so they are not in it. */
    data class ExportSkippedFiles(val fileNames: List<String>) : Message
    data object SaveFailed : Message

    /** The file of the song in the editor is no longer there; the editor's text is, and saving writes it back. */
    data object EditedSongFileGone : Message

    /** A long document's unsaved text did not survive the process being killed in the background. */
    data object EditorDraftLost : Message

    /** The editor was reopened on the unsaved text a previous run left when it ended in the background. */
    data object EditorDraftRestored : Message

    /** A change to the library (a new setlist, a deleted song, a moved entry) that could not be written. */
    data object OperationFailed : Message

    /** The song's file was renamed, but a setlist or its saved transposition still names the old file. */
    data object SongFileRenamedPartly : Message

    /** The song's file was deleted, but a setlist or its saved transposition still names it. */
    data object SongDeletedPartly : Message

    /** A link nothing on this machine would open. The address is shown, since reading it is all that is left. */
    data class LinkNotOpened(val url: String) : Message

    /** The click stopped, or did not start, without being asked to. */
    data class MetronomeStopped(val reason: MetronomeStopReason) : Message

    /** A click that could not sound was stopped as the app left the front, see [CampfireViewModel.onAppStopped]. */
    data object SilentMetronomeStopped : Message
}
