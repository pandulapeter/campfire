/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.export

import com.pandulapeter.campfire.data.model.library.ExportedFile
import com.pandulapeter.campfire.domain.api.export.DiscardExportedFileUseCase
import com.pandulapeter.campfire.presentation.ui.messages.Message
import com.pandulapeter.campfire.presentation.ui.messages.MessageSink
import com.pandulapeter.campfire.presentation.ui.platform.FilePicker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** How [ExportController] hands a file it made to the platform and tells the user how that went. */
internal class ExportSaver(
    private val messageSink: MessageSink,
    private val discardExportedFile: DiscardExportedFileUseCase,
) {

    /**
     * Nothing to export and a picker that threw are the same thing to the user: the file did not come out.
     *
     * An archive over what an import takes is saved all the same, since it is still a complete copy that unzips by
     * hand, but the user is told so the day it is made rather than the day it is needed. [onSaved] runs once the file
     * has been saved, and not for one the user dismissed the dialog of.
     *
     * A saved file is confirmed with [savedMessage], unless something about it is worth saying instead ([warnings],
     * read once the file has been made): each of those already says the file was saved, and a confirmation queued
     * after them would only hold up the one line that matters. A share is not confirmed at all, since the platform's
     * own sheet is what the user sees it go out through, and it cannot tell a share that happened from one dismissed.
     *
     * A file [isWanted] no longer asks for once it is made is dropped without a word: whoever asked for it has gone.
     *
     * A file handed to [filePicker] is the platform's from then on, to remove once nothing reads it (a share sheet may
     * read it after the call returns); one never handed over, or whose hand-over failed, is discarded here.
     */
    suspend fun save(
        filePicker: FilePicker,
        savedMessage: Message,
        isShare: Boolean = false,
        isWanted: () -> Boolean = { true },
        warnings: () -> List<Message> = { emptyList() },
        onSaved: () -> Unit = {},
        export: suspend () -> ExportedFile?,
    ) {
        var made: ExportedFile? = null
        try {
            save(filePicker, savedMessage, isShare, isWanted, warnings, onSaved, export().also { made = it })
        } catch (exception: CancellationException) {
            made?.let { withContext(NonCancellable) { discardExportedFile(it) } }
            throw exception
        } catch (exception: Exception) {
            made?.let { discardExportedFile(it) }
            println("Could not export: ${exception.message}")
            messageSink.sendMessage(Message.ExportFailed)
        }
    }

    private suspend fun save(
        filePicker: FilePicker,
        savedMessage: Message,
        isShare: Boolean,
        isWanted: () -> Boolean,
        warnings: () -> List<Message>,
        onSaved: () -> Unit,
        file: ExportedFile?,
    ) {
        println("Export: ${file?.let { "${it.mimeType}, ${it.content.size} bytes, ${if (isShare) "shared" else "saved"}" } ?: "nothing to export"}")
        when {
            !isWanted() -> file?.let { discardExportedFile(it) }
            file == null -> messageSink.sendMessage(Message.ExportFailed)
            isShare -> filePicker.shareFile(file)
            filePicker.saveFile(file) -> {
                val messages = listOfNotNull(Message.ExportTooLargeToImport.takeIf { file.isTooLargeToImport }) + warnings()
                messages.ifEmpty { listOf(savedMessage) }.forEach(messageSink::sendMessage)
                onSaved()
            }
        }
    }
}
