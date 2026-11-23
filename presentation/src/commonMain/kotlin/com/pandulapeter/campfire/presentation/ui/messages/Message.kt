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

import com.pandulapeter.campfire.data.model.image.ImageRefusal
import com.pandulapeter.campfire.data.model.importing.ImportResult
import com.pandulapeter.campfire.data.model.share.SharingFailure
import com.pandulapeter.campfire.metronome.api.model.MetronomeStopReason
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

    /** The song a preview showed has been written into the library, and the preview has become that song's screen. */
    data object PreviewedSongImported : Message

    /** A batch of one song or setlist the library already holds as it is, which was opened rather than imported. */
    data object AlreadyInLibrary : Message
    data object ExportFailed : Message
    data object PdfSaved : Message
    data object SongExported : Message
    data object SongsExported : Message
    data object SetlistExported : Message
    data object LibraryExported : Message
    data object ProcessLogSaved : Message

    /** A library export refused before anything was written, for holding more files than one archive takes. */
    data object LibraryTooLargeToExport : Message

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
     * the lyrics ([isConverted]) or as ChordPro, or, where it has no chords, as lyrics alone or as the notation
     * ([hasNotation]: an ABC tune with no chord symbols, or a block of another program's notation) it was written in.
     */
    data class NewSongFromText(val isConverted: Boolean, val hasChords: Boolean, val hasNotation: Boolean) : Message

    /** Paste from clipboard, or its shortcut, found no text on the clipboard. */
    data object ClipboardEmpty : Message

    /** The platform would not let the clipboard be read; [text] says where that is decided. */
    data class ClipboardRefused(val text: StringResource) : Message

    /** A pasted or dropped text that is nothing but addresses, which is no song and is never fetched. */
    data object PastedOnlyLinks : Message

    /** A share from another app that was nothing but a web page's address (and its title), which is never fetched. */
    data object SharedLinkNotImported : Message

    /** The page link sheet's Paste, with the clipboard still holding an address rather than the page copied from it. */
    data object PageNotCopiedYet : Message

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

    /** A picture picked for a song that names it but lacks it, which the library does not keep, for [reason]. */
    data class ImageNotAdded(val reason: ImageRefusal) : Message

    /** A picture the user added that was over `ImportLimits.MAX_PICKED_IMAGE_FILE_SIZE`, so it was not even read. */
    data object ImageTooLargeToRead : Message

    /**
     * A file the user picked to attach to a song, which was not kept for [reason]: only [ImageRefusal.TOO_LARGE] (over
     * `ImportLimits.MAX_ATTACHMENT_FILE_SIZE`) and [ImageRefusal.UNSUPPORTED_FORMAT] (a type no song takes) are said.
     */
    data class AttachmentNotAdded(val reason: ImageRefusal) : Message

    /** An attachment opened from a song, whose type nothing on this device opens. */
    data object AttachmentNotOpened : Message

    /** A file picked as an attachment after Android ended the app, with nowhere left to put it. */
    data object AttachmentPickLost : Message

    /** A picture the user added was too large for a song, and a smaller copy of it was kept instead. */
    data object ImageShrunk : Message

    /** A picture the user added was of a format some platform cannot show, and a copy in one every platform can was kept. */
    data object ImageConverted : Message

    /** A picture link that answered with nothing, or an address the app does not ask (not public `https`). */
    data object PictureLinkMissing : Message

    /** A picture link that answered with something other than a picture, most often the page showing it. */
    data object PictureLinkNotPicture : Message

    /**
     * A picture link that did not answer, which in a browser is also how a site that does not allow other pages to
     * download its pictures answers: the two cannot be told apart, so the message names both and the way around them.
     */
    data object PictureLinkUnreachable : Message

    /** A change to the library (a new setlist, a deleted song, a moved entry) that could not be written. */
    data object OperationFailed : Message

    /** A write refused because the device has no room left, said instead of the failure it would otherwise be. */
    data object StorageFull : Message

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
