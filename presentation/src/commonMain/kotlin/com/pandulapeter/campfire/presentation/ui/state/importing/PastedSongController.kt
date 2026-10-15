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
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogHost
import com.pandulapeter.campfire.presentation.ui.messages.Message
import com.pandulapeter.campfire.presentation.ui.messages.MessageSink
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.platform.ClipboardText
import com.pandulapeter.campfire.presentation.ui.platform.ClipboardTextReader
import com.pandulapeter.campfire.presentation.ui.platform.fitsImport
import com.pandulapeter.campfire.presentation.ui.platform.isOnlyLinks
import com.pandulapeter.campfire.presentation.ui.platform.sharedTextsToImportedFiles
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * A text that is to become a new song: the clipboard's, through the Songs screen's Paste from clipboard or Ctrl / Cmd + V,
 * or a text dropped onto the window. It is named and sent down the import as the `.txt` file Android's shared text
 * is (see [sharedTextsToImportedFiles]), so it is converted, split and compared exactly as that file would be, and the
 * import decides what it comes to (see [ImportController.enqueueImport]'s `isNewSongFromText`).
 *
 * @param enqueueImport Queues the text's file, as a new song where that is true and as an ordinary import otherwise.
 */
internal class PastedSongController(
    private val scope: CoroutineScope,
    private val messageSink: MessageSink,
    private val dialogHost: DialogHost,
    private val backStack: List<CampfireDestination>,
    private val isImporting: StateFlow<Boolean>,
    private val isPerformanceModeEnabled: StateFlow<Boolean>,
    private val enqueueImport: (file: ImportedFile, isNewSongFromText: Boolean) -> Unit,
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
     * The one way from a text to a new song. Read only mode offers none of its own ways in, so a text that still arrives
     * there is written as a song the system handed over would be, and offered to open, rather than put into a form
     * that mode never shows.
     *
     * @param subject What the text was called where it came from, which names it where the text declares no title.
     */
    fun newSongFromText(text: String, subject: String? = null) {
        when {
            text.isBlank() -> messageSink.sendMessage(Message.ClipboardEmpty)
            text.isOnlyLinks() -> messageSink.sendMessage(Message.PastedOnlyLinks)
            !text.fitsImport() -> messageSink.sendMessage(Message.PastedTextTooLarge)
            else -> enqueueImport(sharedTextsToImportedFiles(listOf(text), subject).single(), !isPerformanceModeEnabled.value)
        }
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
