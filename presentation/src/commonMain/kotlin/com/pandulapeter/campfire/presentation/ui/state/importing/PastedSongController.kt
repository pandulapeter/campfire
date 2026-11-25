/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state.importing

import com.pandulapeter.campfire.chordpro.convert.PageTitle
import com.pandulapeter.campfire.data.model.importing.PageLineMarks
import com.pandulapeter.campfire.data.model.share.SharedLink
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogHost
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.messages.Message
import com.pandulapeter.campfire.presentation.ui.messages.MessageSink
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.platform.ClipboardText
import com.pandulapeter.campfire.presentation.ui.platform.ClipboardTextReader
import com.pandulapeter.campfire.presentation.ui.platform.SharedTextPayload
import com.pandulapeter.campfire.presentation.ui.platform.fitsImport
import com.pandulapeter.campfire.presentation.ui.platform.isLinkWithTitle
import com.pandulapeter.campfire.presentation.ui.platform.isOnlyLinks
import com.pandulapeter.campfire.presentation.ui.platform.pageAddressIn
import com.pandulapeter.campfire.presentation.ui.platform.sharedTextsToImportedFiles
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * A text that is to become a new song: the clipboard's, through the Songs screen's Paste from clipboard or Ctrl / Cmd + V,
 * a text dropped onto the window, or one shared to Campfire on either phone. It is named and sent down the import as a
 * `.txt` file (see [sharedTextsToImportedFiles]), so it is converted, split and compared exactly as that file would be,
 * and the import decides what it comes to (see [ImportController.enqueueImport]'s `isNewSongFromText`).
 *
 * @param enqueueImport Queues the source, as a new song where that is true and as an ordinary import otherwise, with the
 *   title and artist the sender's subject suggests for a new song, and returns what completes once it has settled.
 * @param importSharedLink Imports a text that is a Campfire link as the link it is, see [sharedLinkIn].
 * @param canLoadPages Whether this platform can read a page by its address at all (`CanLoadPagesUseCase`), asked only
 *   when [isPageLoadingSupported] is read: the desktop's answer may still be on its way while the view model is built.
 * @param isPageLoadingEnabled Whether the user lets it (Settings → Features → Pages from links).
 */
internal class PastedSongController(
    private val scope: CoroutineScope,
    private val messageSink: MessageSink,
    private val dialogHost: DialogHost,
    private val backStack: List<CampfireDestination>,
    private val isImporting: StateFlow<Boolean>,
    private val isPerformanceModeEnabled: StateFlow<Boolean>,
    private val enqueueImport: (source: ImportSource, isNewSongFromText: Boolean, suggestion: PageTitle?) -> Deferred<Unit>,
    private val importSharedLink: (SharedLink) -> Deferred<Unit>,
    private val canLoadPages: () -> Boolean,
    private val isPageLoadingEnabled: () -> Boolean,
) {

    /** Whether this platform can read a page by its address at all. */
    val isPageLoadingSupported get() = canLoadPages()

    /**
     * Whether a dropped text is answered now: only with the Songs screen on top and nothing over it - a dialog, a sheet
     * or an overflow menu would have the song land under it. Read only mode included, as for a shared text: a drop is the
     * user bringing a song in on purpose, and nothing is written before the editor's Save.
     */
    val isNewSongDropAvailable
        get() = backStack.lastOrNull() == CampfireDestination.Songs && dialogHost.visibleDialog.value == null &&
            !dialogHost.overlayState.isAnyMenuOpen

    /**
     * Whether the paste shortcut is answered now: where a drop would be, except in read only mode, where a stray
     * Ctrl / Cmd + V is not taken for a new song.
     */
    val isNewSongPasteAvailable
        get() = isNewSongDropAvailable && !isPerformanceModeEnabled.value

    /**
     * The one way from a text to a new song, which opens the editor on it, read only mode included: a text shared there
     * is the user asking for a song, and the share is the one way in that mode leaves. One that arrives while the editor
     * holds unsaved text waits in the import queue for that editor to be left. A text that is a Campfire link, and nothing
     * else, is that link's import instead, and one that is another page's address the page read once on the device, where
     * the platform and the Pages from links switch allow it (see [pageAddressIn]); where the platform cannot read pages at
     * all, the address opens [DialogType.PageLink], which walks the user through copying the page in their browser.
     *
     * @param subject What the text was called where it came from, which titles the new song, and names its artist where
     *   it says "<title> Chords by <artist>", for what the text does not declare.
     * @return Completed once the text has settled: at once where it is answered with a message alone.
     */
    fun newSongFromText(text: String, subject: String? = null) = newSongFromText(text = text, subject = subject, onlyLinks = Message.PastedOnlyLinks, marks = null)

    /**
     * [newSongFromText], [onlyLinks] saying why a text of addresses alone is no song where its page is not read, and
     * [marks] the structure marks of its lines where it is a page the system's browser read (`SharedTextPayload.marks`).
     */
    private fun newSongFromText(text: String, subject: String?, onlyLinks: Message, marks: List<PageLineMarks?>?): Deferred<Unit> {
        sharedLinkIn(text)?.let { return importSharedLink(it) }
        val pageAddress = pageAddressIn(text)
        return when {
            text.isBlank() -> answered(Message.ClipboardEmpty)
            pageAddress != null && !isPageLoadingSupported -> {
                dialogHost.showDialog(DialogType.PageLink(pageAddress))
                CompletableDeferred(Unit)
            }
            pageAddress != null && isPageLoadingEnabled() -> enqueueImport(ImportSource.Page(pageAddress), true, PageTitle.of(subject))
            text.isOnlyLinks() || text.isLinkWithTitle() -> answered(onlyLinks)
            !text.fitsImport() -> answered(Message.PastedTextTooLarge)
            else -> pageTextImport(text = text, subject = subject, marks = marks)
        }
    }

    /** From a web page or another app, so its page clutter is cropped (`ImportedFile.isPageText`). */
    private fun pageTextImport(text: String, subject: String?, marks: List<PageLineMarks?>?) = enqueueImport(
        ImportSource.Files(sharedTextsToImportedFiles(listOf(text), subject).map { it.copy(isPageText = true, pageMarks = marks) }),
        true,
        PageTitle.of(subject),
    )

    /**
     * Texts shared to Campfire from another app. One text is a song to make, as a paste is, or the link's import where it
     * is one Campfire link, or the page's where it is another page's address; several are an import, each a file of its
     * own. A share of nothing but other addresses that is not read as a page - several, or with Pages from links off - is
     * answered with a sentence. A share with nothing in it at all still goes to the import, which reports it as skipped: the user picked
     * Campfire in a share sheet and is owed an answer.
     *
     * @return Completed once the share has settled, which is when iOS lets go of it in the share extension's inbox.
     */
    fun importSharedText(payload: SharedTextPayload): Deferred<Unit> {
        val texts = payload.texts.filter { it.isNotBlank() }
        return when {
            texts.isEmpty() -> enqueueImport(ImportSource.Files(sharedTextsToImportedFiles(payload.texts, payload.subject)), false, null)
            texts.size == 1 -> newSongFromText(
                text = texts.single(),
                subject = payload.subject,
                onlyLinks = Message.SharedLinkNotImported,
                marks = payload.marks.takeIf { payload.texts.size == 1 },
            )
            texts.all { it.isOnlyLinks() || it.isLinkWithTitle() } -> answered(Message.SharedLinkNotImported)
            else -> enqueueImport(ImportSource.Files(sharedTextsToImportedFiles(texts, payload.subject)), false, null)
        }
    }

    private fun answered(message: Message): Deferred<Unit> {
        messageSink.sendMessage(message)
        return CompletableDeferred(Unit)
    }

    /**
     * Launched here rather than by the menu it is chosen from, which is gone before the read returns: iOS's Allow Paste
     * takes as long as the user takes to answer it. Not started while an import is running, as Import files is not.
     */
    fun newSongFromClipboard(reader: ClipboardTextReader) {
        if (isImporting.value) return
        scope.launch {
            when (val clipboardText = readClipboard(reader)) {
                is ClipboardText.Text -> newSongFromText(clipboardText.value)
                ClipboardText.None -> messageSink.sendMessage(Message.ClipboardEmpty)
                is ClipboardText.Refused -> messageSink.sendMessage(Message.ClipboardRefused(clipboardText.message))
            }
        }
    }

    /**
     * [DialogType.PageLink]'s Paste: the page the user copied in their browser, read from the clipboard and made a new
     * song as a paste is, after [onAccepted] has put away what asked for it. A clipboard still holding an address (the
     * page's, or links alone) is no page yet, and is answered with a sentence in the sheet, which stays up for the copy;
     * nothing on the clipboard, or a refusal, is answered as the paste shortcut answers it. Launched here for the reason
     * [newSongFromClipboard] is.
     */
    fun newSongFromCopiedPage(reader: ClipboardTextReader, onAccepted: () -> Unit) {
        if (isImporting.value) return
        scope.launch {
            val clipboardText = readClipboard(reader)
            val text = (clipboardText as? ClipboardText.Text)?.value.orEmpty()
            val refusal = when {
                clipboardText is ClipboardText.Refused -> Message.ClipboardRefused(clipboardText.message)
                text.isBlank() -> Message.ClipboardEmpty
                pageAddressIn(text) != null || text.isOnlyLinks() || text.isLinkWithTitle() -> Message.PageNotCopiedYet
                !text.fitsImport() -> Message.PastedTextTooLarge
                else -> null
            }
            if (refusal != null) {
                println("Copied page not pasted: ${refusal::class.simpleName}")
                return@launch messageSink.sendMessage(refusal)
            }
            println("Copied page pasted")
            onAccepted()
            pageTextImport(text = text, subject = null, marks = null)
        }
    }

    private suspend fun readClipboard(reader: ClipboardTextReader) = try {
        reader.read()
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        println("Could not read the clipboard: ${exception.message}")
        ClipboardText.None
    }
}
