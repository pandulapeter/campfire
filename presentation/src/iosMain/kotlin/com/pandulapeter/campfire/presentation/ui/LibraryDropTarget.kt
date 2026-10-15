/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui

import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import com.pandulapeter.campfire.data.model.importing.ImportBudget
import com.pandulapeter.campfire.data.model.importing.ImportedFile
import com.pandulapeter.campfire.presentation.ui.state.importing.isDropAvailable
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import platform.Foundation.NSData
import platform.Foundation.NSDataReadingMappedIfSafe
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSItemProvider
import platform.Foundation.NSLock
import platform.Foundation.NSNumber
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfURL
import platform.UniformTypeIdentifiers.UTType
import platform.posix.memcpy

/**
 * Files dragged onto the app - from Files or another app beside it on an iPad, or from one in the background on an
 * iPhone - are imported, as the desktop's dropped files are, and a dragged text is a new song where Paste from
 * clipboard would make one. A drag that carries both is its files. Read only mode takes neither.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun Modifier.libraryDropTarget(viewModel: CampfireViewModel): Modifier {
    val scope = rememberCoroutineScope()
    val target = remember(viewModel, scope) {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                // Asked again rather than trusted from the start of the drag, during which a dialog, the editor or
                // read only mode can have come.
                if (!viewModel.isDropAvailable) return false
                val providers = event.items.map { it.itemProvider }
                val fileProviders = providers.filterNot { it.isText }
                if (fileProviders.isEmpty()) {
                    if (!viewModel.pastedSongController.isNewSongPasteAvailable) return false
                    val texts = providers.map { it.loadText() }
                    scope.launch {
                        val text = texts.awaitAll().filterNotNull().joinToString("\n")
                        if (text.isNotEmpty()) viewModel.pastedSongController.newSongFromText(text)
                    }
                    return true
                }
                // An item provider is only asked while the drop is being answered, so every load starts here; one
                // budget across them keeps the drop within what a pick may read, whichever finishes first.
                val budget = LockedImportBudget()
                val files = fileProviders.map { it.loadFile(budget) }
                scope.launch { viewModel.importController.importFiles(files.awaitAll()) }
                return true
            }
        }
    }
    return dragAndDropTarget(
        shouldStartDragAndDrop = { event ->
            val providers = event.items.map { it.itemProvider }
            viewModel.isDropAvailable && providers.isNotEmpty() &&
                (providers.any { !it.isText } || viewModel.pastedSongController.isNewSongPasteAvailable)
        },
        target = target,
    )
}

/**
 * A selection dragged out of a text view offers itself as UTF-8 text, which a text file dragged out of Files does not:
 * that one offers its own type (`public.plain-text`, or Campfire's ChordPro types), and is read as the file it is.
 */
private val NSItemProvider.isText get() = hasItemConformingToTypeIdentifier(UTF8_PLAIN_TEXT)

/** Null where the text could not be loaded, which leaves only what the other items held. */
@OptIn(BetaInteropApi::class)
private fun NSItemProvider.loadText(): CompletableDeferred<String?> {
    val text = CompletableDeferred<String?>()
    loadDataRepresentationForTypeIdentifier(UTF8_PLAIN_TEXT) { data, error ->
        error?.let { println("Could not read the dropped text: ${it.localizedDescription}") }
        text.complete(data?.let { NSString.create(data = it, encoding = NSUTF8StringEncoding)?.toString() })
    }
    return text
}

/**
 * The provider's copy of the file exists only until its completion handler returns, so it is read there, on whatever
 * queue the provider answers on. One that cannot be read arrives empty, which the import reports as skipped.
 */
@OptIn(ExperimentalForeignApi::class)
private fun NSItemProvider.loadFile(budget: LockedImportBudget): CompletableDeferred<ImportedFile> {
    val file = CompletableDeferred<ImportedFile>()
    val typeIdentifier = registeredTypeIdentifiers.filterIsInstance<String>().firstOrNull()
    val name = fileNameOf(suggestedName, typeIdentifier)
    if (typeIdentifier == null) {
        file.complete(ImportedFile.unread(name))
        return file
    }
    loadFileRepresentationForTypeIdentifier(typeIdentifier) { url, error ->
        error?.let { println("Could not read \"$name\": ${it.localizedDescription}") }
        val size = url?.path?.let { NSFileManager.defaultManager.attributesOfItemAtPath(it, error = null) }?.get(NSFileSize) as? NSNumber
        val read = url?.let {
            budget.read(name = name, size = size?.longLongValue) { limit ->
                // Mapped rather than loaded, so that a file over the limit says so before any of it is copied.
                NSData.dataWithContentsOfURL(url, options = NSDataReadingMappedIfSafe, error = null)
                    ?.let { data -> if (data.length.toLong() > limit) ByteArray(limit.toInt() + 1) else data.toByteArray() }
            }
        }
        file.complete(read ?: ImportedFile.unread(name))
    }
    return file
}

/**
 * The import goes by the extension, which a provider's suggested name usually leaves off (Files names a song "Wonderwall",
 * not "Wonderwall.cho"), so the one its type is known by is put back.
 */
private fun fileNameOf(suggestedName: String?, typeIdentifier: String?): String {
    val name = suggestedName?.takeIf { it.isNotBlank() } ?: UNTITLED
    val extension = typeIdentifier?.let { UTType.typeWithIdentifier(it)?.preferredFilenameExtension } ?: return name
    return if (name.endsWith(".$extension", ignoreCase = true)) name else "$name.$extension"
}

/** Item providers answer on queues of their own, side by side, and an [ImportBudget] is not to be read from two at once. */
private class LockedImportBudget {

    private val budget = ImportBudget()
    private val lock = NSLock()

    fun read(name: String, size: Long?, readBytes: (limit: Long) -> ByteArray?): ImportedFile? {
        lock.lock()
        return try {
            budget.read(name = name, size = size, readBytes = readBytes)
        } finally {
            lock.unlock()
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray() = ByteArray(length.toInt()).also { bytes ->
    if (bytes.isNotEmpty()) {
        bytes.usePinned { memcpy(it.addressOf(0), this.bytes, length) }
    }
}

private const val UTF8_PLAIN_TEXT = "public.utf8-plain-text"
private const val UNTITLED = "Untitled"
