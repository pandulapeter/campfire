/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire

import com.pandulapeter.campfire.data.model.domain.ExportedFile
import com.pandulapeter.campfire.data.model.domain.ImportBudget
import com.pandulapeter.campfire.data.model.domain.ImportedFile
import com.pandulapeter.campfire.presentation.ui.platform.FilePicker
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSDataReadingMappedIfSafe
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.writeToURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIAdaptivePresentationControllerDelegateProtocol
import platform.UIKit.UIPresentationController
import platform.UIKit.popoverPresentationController
import platform.UIKit.presentationController
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTTypeData
import platform.UniformTypeIdentifiers.UTTypeZIP
import platform.darwin.NSObject
import platform.posix.memcpy
import kotlin.coroutines.resume

/**
 * The iOS document picker. It lives in the app module rather than in `:presentation` so that the shared UI module
 * stays free of UIKit, the same reason opening a URL is handed in from here.
 *
 * @param viewController The controller to present the picker from, which is the one hosting the Compose UI.
 */
internal class IosFilePicker(
    private val viewController: () -> UIViewController,
) : FilePicker {

    // UIKit keeps only a weak reference to a delegate, so the one that is in flight is held here.
    private var delegate: NSObject? = null

    override suspend fun pickFiles(): List<ImportedFile> {
        val urls = suspendCancellableCoroutine<List<NSURL>> { continuation ->
            // Everything, not just plain text: ".cho" is not a type iOS knows, so a narrower list would grey the songs
            // out in the picker. What is not a song is skipped by the import and reported afterwards.
            val controller = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeData, UTTypeZIP), asCopy = true)
            controller.allowsMultipleSelection = true
            present(controller) { urls -> continuation.resume(urls) }
        }
        return withContext(Dispatchers.IO) {
            val budget = ImportBudget()
            urls.mapNotNull { it.readImportedFile(budget) }
        }
    }

    override suspend fun saveFile(file: ExportedFile): Boolean {
        // The picker exports a file that already exists, so the bytes go to a temporary one first.
        val url = file.writeToTemporaryFile() ?: return false
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                present(UIDocumentPickerViewController(forExportingURLs = listOf(url))) { urls -> continuation.resume(urls.isNotEmpty()) }
            }
        }
    }

    override val canShare = true

    override suspend fun shareFile(file: ExportedFile): Boolean {
        val url = file.writeToTemporaryFile() ?: return false
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                val host = viewController()
                // UIKit refuses a second presentation with nothing but a log line, so a share asked for while another
                // sheet is up - or is still sliding away, which is where a picker's own callback leaves it - would
                // otherwise be reported as a share that happened.
                if (host.presentedViewController != null) {
                    continuation.resume(false)
                } else {
                    val controller = UIActivityViewController(activityItems = listOf(url), applicationActivities = null)
                    // An iPad presents this as a popover, which needs something to point at; the whole view will do.
                    controller.popoverPresentationController?.sourceView = host.view
                    host.presentViewController(controller, animated = true, completion = null)
                    // Resumed as the sheet is shown rather than from its completionWithItemsHandler: nothing acts on
                    // whether a share was completed, and a handler UIKit never calls - it promises nothing for a
                    // controller torn down underneath it - would leave the caller suspended, holding the one file
                    // transfer and so every later export, share and import. Android's chooser is fire and forget too.
                    continuation.resume(true)
                }
            }
        }
    }

    private fun present(controller: UIDocumentPickerViewController, onFinished: (List<NSURL>) -> Unit) {
        val host = viewController()
        // UIKit refuses a second presentation with nothing but a log line, and the answer it would have given never
        // comes: the caller is told nothing was picked instead of waiting for good.
        if (host.presentedViewController != null) {
            onFinished(emptyList())
            return
        }
        // Nothing is on screen, so a delegate still held is one whose picker went away without telling it: its caller
        // is answered now rather than never.
        (delegate as? DocumentPickerDelegate)?.finish(emptyList())
        val pickerDelegate = DocumentPickerDelegate { urls ->
            delegate = null
            onFinished(urls)
        }
        delegate = pickerDelegate
        controller.delegate = pickerDelegate
        // A sheet swiped away tells neither of the picker's own callbacks, only its presentation controller.
        controller.presentationController?.delegate = pickerDelegate
        host.presentViewController(controller, animated = true, completion = null)
    }

}

/**
 * Null when the file cannot be read, so that one bad file does not lose the ones next to it. Foundation reports
 * that by handing back nothing rather than by throwing. Read within [budget], so a file the import would not look
 * inside, or one too large for it, is handed over unread.
 *
 * The copies the picker hands over live in the app's own container, but a URL that arrives from another app is
 * security scoped, so the read happens inside the access it grants. Blocking, so not for the main thread.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun NSURL.readImportedFile(budget: ImportBudget): ImportedFile? {
    val isAccessible = startAccessingSecurityScopedResource()
    return try {
        val size = path?.let { NSFileManager.defaultManager.attributesOfItemAtPath(it, error = null) }?.get(NSFileSize) as? NSNumber
        budget.read(name = lastPathComponent.orEmpty(), size = size?.longLongValue) { limit ->
            // Mapped rather than loaded, so that a file whose attributes said nothing still gives its length away
            // before any of it is copied into the heap. Longer than the limit, it only has to say so, which the
            // budget takes one byte past the limit to mean.
            val data = NSData.dataWithContentsOfURL(this, options = NSDataReadingMappedIfSafe, error = null)
            if (data == null) {
                println("Could not read \"$this\".")
            }
            data?.let { if (it.length.toLong() > limit) ByteArray(limit.toInt() + 1) else it.toByteArray() }
        }
    } finally {
        if (isAccessible) {
            stopAccessingSecurityScopedResource()
        }
    }
}

/**
 * The bytes on disk, which is what both the picker and the share sheet take: neither takes bytes. Null where the
 * write failed, which the caller reports as an export that did not come out.
 *
 * On [Dispatchers.IO] because a whole-library archive is megabytes and both callers are called on the main
 * dispatcher, where copying and writing it would freeze the app until it was done.
 */
private suspend fun ExportedFile.writeToTemporaryFile(): NSURL? = withContext(Dispatchers.IO) {
    val url = NSURL.fileURLWithPath(NSTemporaryDirectory() + name)
    if (bytes.toNSData().writeToURL(url, atomically = true)) url else null
}

/**
 * Answers its picker's caller at most once, however many of the ways a picker can go away report it: a late callback
 * of a picker whose caller was already answered must not resume that caller's continuation a second time.
 */
private class DocumentPickerDelegate(
    private val onFinished: (List<NSURL>) -> Unit,
) : NSObject(), UIDocumentPickerDelegateProtocol, UIAdaptivePresentationControllerDelegateProtocol {

    private var isFinished = false

    fun finish(urls: List<NSURL>) {
        if (isFinished) return
        isFinished = true
        onFinished(urls)
    }

    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) =
        finish(didPickDocumentsAtURLs.filterIsInstance<NSURL>())

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) = finish(emptyList())

    override fun presentationControllerDidDismiss(presentationController: UIPresentationController) = finish(emptyList())
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray() = ByteArray(length.toInt()).also { bytes ->
    if (bytes.isNotEmpty()) {
        bytes.usePinned { memcpy(it.addressOf(0), this.bytes, length) }
    }
}

@OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)
private fun ByteArray.toNSData() = if (isEmpty()) {
    NSData()
} else {
    usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }
}
