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

import com.pandulapeter.campfire.data.model.importing.ImportResult
import com.pandulapeter.campfire.data.model.share.SharingFailure
import com.pandulapeter.campfire.metronome.api.model.MetronomeStopReason
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import org.jetbrains.compose.resources.StringResource

/** Something that has happened and is worth one line of text at the bottom of the screen. */
sealed interface Message {
    /**
     * @param hasDetails Whether the snackbar offers the import screen, for an outcome that needed none but has
     *   more to it than the counts fit into one line: a batch of more than one file, or one whose screen was left
     *   while it was being written.
     */
    data class ImportFinished(val result: ImportResult, val hasDetails: Boolean) : Message

    data object ImportFailed : Message

    /** A restored process found that the one before it ended behind an import that had not been written yet. */
    data object ImportInterrupted : Message
    data object ExportFailed : Message
    data object PdfSaved : Message
    data object SongExported : Message
    data object SongsExported : Message
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

    /** A song with no file yet was not saved, since it has no title to name its file by. */
    data object NewSongUntitled : Message

    /**
     * The editor was opened on a pasted, dropped or shared text as a new song: how the text was read, from chords over
     * the lyrics ([isConverted]) or as ChordPro, or as lyrics alone where it has no chords.
     */
    data class NewSongFromText(val isConverted: Boolean, val hasChords: Boolean) : Message

    /** Paste from clipboard, or its shortcut, found no text on the clipboard. */
    data object ClipboardEmpty : Message

    /** The platform would not let the clipboard be read; [text] says where that is decided. */
    data class ClipboardRefused(val text: StringResource) : Message

    /** A pasted or dropped text that is nothing but addresses, which is no song and is never fetched. */
    data object PastedOnlyLinks : Message

    /** A share from another app that was nothing but a web page's address (and its title), which is never fetched. */
    data object SharedLinkNotImported : Message

    /** A Campfire link in a format a later version made, which this one cannot open; updating can. */
    data object SharedLinkFromNewerVersion : Message

    /** A link was put on the clipboard, which only some platforms say by themselves. */
    data object LinkCopied : Message

    /** A link's QR code was saved as a picture. */
    data object QrCodeSaved : Message

    /** A share could not be stopped, for [reason]; its link still works. */
    data class StopSharingFailed(val reason: SharingFailure) : Message

    /** Some of the shares Stop sharing everything was asked to stop are still shared. */
    data class StopSharingEverythingFailed(val reason: SharingFailure) : Message

    /**
     * A reconnect of a working connection failed before the new permission was stored; the connection the user had
     * still works.
     */
    data object ReconnectFailed : Message

    /**
     * The first run's onboarding was left before its last page, which offers to open the link the clipboard seems to hold:
     * the offer, made once more, with an action that takes it.
     */
    data object ClipboardLinkOffered : Message

    /** The onboarding's offer to open a copied link found something else on the clipboard when it was taken. */
    data object ClipboardNotCampfireLink : Message

    /** A pasted or dropped text longer than an import reads of one file. */
    data object PastedTextTooLarge : Message

    /** A change to the library (a new setlist, a deleted song, a moved entry) that could not be written. */
    data object OperationFailed : Message

    /** The tags or the languages of the selection, written into [count] songs. */
    data class SongsUpdated(val count: Int) : Message

    /** The tags or the languages of the selection, which [count] of its songs could not be read or written for. */
    data class SongsNotUpdated(val count: Int) : Message

    /** The song's file was renamed, but a setlist or its saved transposition still names the old file. */
    data object SongFileRenamedPartly : Message

    /** The song's file was deleted, but a setlist or its saved transposition still names it. */
    data object SongDeletedPartly : Message

    /** A link nothing on this machine would open. The address is shown, since reading it is all that is left. */
    data class LinkNotOpened(val url: String) : Message

    /** The click stopped, or did not start, without being asked to. */
    data class MetronomeStopped(val reason: MetronomeStopReason) : Message

    /** A click that could not sound was stopped as the app left the front, see `MetronomeController.onAppStopped`. */
    data object SilentMetronomeStopped : Message
}
