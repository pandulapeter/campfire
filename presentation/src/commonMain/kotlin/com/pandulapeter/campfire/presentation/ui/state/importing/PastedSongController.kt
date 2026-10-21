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

import com.pandulapeter.campfire.data.model.importing.ImportedFile
import com.pandulapeter.campfire.data.model.share.SharedLink
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogHost
import com.pandulapeter.campfire.presentation.ui.messages.Message
import com.pandulapeter.campfire.presentation.ui.messages.MessageSink
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.platform.ClipboardText
import com.pandulapeter.campfire.presentation.ui.platform.ClipboardTextReader
import com.pandulapeter.campfire.presentation.ui.platform.SharedTextPayload
import com.pandulapeter.campfire.presentation.ui.platform.SharedTextTitle
import com.pandulapeter.campfire.presentation.ui.platform.fitsImport
import com.pandulapeter.campfire.presentation.ui.platform.isLinkWithTitle
import com.pandulapeter.campfire.presentation.ui.platform.isOnlyLinks
import com.pandulapeter.campfire.presentation.ui.platform.sharedTextTitle
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
 * @param enqueueImport Queues the files, as a new song where that is true and as an ordinary import otherwise, with the
 *   title and artist the sender's subject suggests for a new song, and returns what completes once they have settled.
 * @param importSharedLink Imports a text that is a Campfire link as the link it is, see [sharedLinkIn].
 */
internal class PastedSongController(
    private val scope: CoroutineScope,
    private val messageSink: MessageSink,
    private val dialogHost: DialogHost,
    private val backStack: List<CampfireDestination>,
    private val isImporting: StateFlow<Boolean>,
    private val isPerformanceModeEnabled: StateFlow<Boolean>,
    private val enqueueImport: (files: List<ImportedFile>, isNewSongFromText: Boolean, suggestion: SharedTextTitle?) -> Deferred<Unit>,
    private val importSharedLink: (SharedLink) -> Deferred<Unit>,
) {

    /**
     * Whether the shortcut and a dropped text are answered now: only with the Songs screen on top and nothing over it - a
     * dialog, a sheet or an overflow menu would have the paste land under it - and not in read only mode, which offers
     * no way to make a song.
     */
    val isNewSongPasteAvailable
        get() = backStack.lastOrNull() == CampfireDestination.Songs && dialogHost.visibleDialog.value == null &&
            !dialogHost.overlayState.isAnyMenuOpen && !isPerformanceModeEnabled.value

    /**
     * The one way from a text to a new song, which opens the editor on it, read only mode included: a text shared there
     * is the user asking for a song, and the share is the one way in that mode leaves. One that arrives while the editor
     * holds unsaved text waits in the import queue for that editor to be left. A text that is a Campfire link, and nothing
     * else, is that link's import instead.
     *
     * @param subject What the text was called where it came from, which titles the new song, and names its artist where
     *   it says "<title> Chords by <artist>", for what the text does not declare.
     * @return Completed once the text has settled: at once where it is answered with a message alone.
     */
    fun newSongFromText(text: String, subject: String? = null): Deferred<Unit> {
        sharedLinkIn(text)?.let { return importSharedLink(it) }
        return newSongFromOtherText(text, subject)
    }

    private fun newSongFromOtherText(text: String, subject: String?): Deferred<Unit> = when {
        text.isBlank() -> answered(Message.ClipboardEmpty)
        text.isOnlyLinks() || text.isLinkWithTitle() -> answered(Message.PastedOnlyLinks)
        !text.fitsImport() -> answered(Message.PastedTextTooLarge)
        else -> enqueueImport(sharedTextsToImportedFiles(listOf(text), subject), true, sharedTextTitle(subject))
    }

    /**
     * Texts shared to Campfire from another app. One text is a song to make, as a paste is, or the link's import where it
     * is one Campfire link; several are an import, each a file of its own. A share of nothing but other addresses - a web
     * page shared by its link, alone or beside a one-line title - is answered with a sentence, since the page is never
     * fetched. A share with nothing in it at all still goes to the import, which reports it as skipped: the user picked
     * Campfire in a share sheet and is owed an answer.
     *
     * @return Completed once the share has settled, which is when iOS lets go of it in the share extension's inbox.
     */
    fun importSharedText(payload: SharedTextPayload): Deferred<Unit> {
        val texts = payload.texts.filter { it.isNotBlank() }
        return when {
            texts.isEmpty() -> enqueueImport(sharedTextsToImportedFiles(payload.texts, payload.subject), false, null)
            texts.size == 1 && sharedLinkIn(texts.single()) != null -> newSongFromText(texts.single(), payload.subject)
            texts.all { it.isOnlyLinks() || it.isLinkWithTitle() } -> answered(Message.SharedLinkNotImported)
            texts.size == 1 -> newSongFromText(texts.single(), payload.subject)
            else -> enqueueImport(sharedTextsToImportedFiles(texts, payload.subject), false, null)
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
            val clipboardText = try {
                reader.read()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                println("Could not read the clipboard: ${exception.message}")
                ClipboardText.None
            }
            when (clipboardText) {
                is ClipboardText.Text -> newSongFromText(clipboardText.value)
                ClipboardText.None -> messageSink.sendMessage(Message.ClipboardEmpty)
                is ClipboardText.Refused -> messageSink.sendMessage(Message.ClipboardRefused(clipboardText.message))
            }
        }
    }
}
