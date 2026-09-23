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
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSData
import platform.Foundation.NSDataReadingMappedIfSafe
import platform.Foundation.NSError
import platform.Foundation.NSFileCoordinator
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
            urls.mapNotNull { url ->
                url.readImportedFile(budget).also {
                    // The picker was asked for copies, so this file is the app's own and nobody else's. Deleted
                    // whether it could be read or not: nothing will come back for it. A file opened in place is
                    // never one of these - that path is IosFileImport's, and it makes the same distinction.
                    if (url.isTemporaryCopy()) url.deleteTemporaryFile()
                }
            }
        }
    }

    override suspend fun saveFile(file: ExportedFile): Boolean {
        // The picker exports a file that already exists, so the bytes go to a temporary one first.
        val url = file.writeToTemporaryFile() ?: return false
        return try {
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { continuation ->
                    present(UIDocumentPickerViewController(forExportingURLs = listOf(url))) { urls -> continuation.resume(urls.isNotEmpty()) }
                }
            }
        } finally {
            // After the picker has answered, never before: an export that the user confirmed is the picker moving
            // this very file to where they chose. What is left here is the cancelled case - and, where the move
            // happened, a file that is already gone, which removing is a no-op.
            withContext(NonCancellable + Dispatchers.IO) { url.deleteTemporaryFile() }
        }
    }

    override val canShare = true

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun shareFile(file: ExportedFile): Boolean {
        val url = file.writeToTemporaryFile() ?: return false
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                val host = viewController()
                // UIKit refuses a second presentation with nothing but a log line, so a share asked for while another
                // sheet is up - or is still sliding away, which is where a picker's own callback leaves it - would
                // otherwise be reported as a share that happened.
                if (host.presentedViewController != null) {
                    url.deleteTemporaryFile()
                    continuation.resume(false)
                } else {
                    val controller = UIActivityViewController(activityItems = listOf(url), applicationActivities = null)
                    // An iPad presents this as a popover, which needs something to point at. The menu the share was chosen
                    // from is Compose's and out of reach from here, so the popover stands in the middle of the view with no
                    // arrow rather than pointing at the view's corner, which is where a source rectangle left at zero is.
                    controller.popoverPresentationController?.let { popover ->
                        val bounds = host.view.bounds
                        popover.sourceView = host.view
                        popover.sourceRect = bounds.useContents { CGRectMake(origin.x + size.width / 2, origin.y + size.height / 2, 0.0, 0.0) }
                        popover.permittedArrowDirections = 0uL
                    }
                    // The sheet hands out copies and never takes the file away, so this is the only moment it is
                    // certainly finished with: an activity that is still reading it has not returned yet. A handler
                    // that never runs costs one file that iOS purges on its own, which is why the caller is still
                    // answered from the presentation rather than from here.
                    controller.completionWithItemsHandler = { _, _, _, _ -> url.deleteTemporaryFile() }
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
 * security scoped, so the read happens inside the access it grants.
 *
 * Read through an `NSFileCoordinator`, because `LSSupportsOpeningDocumentsInPlace` means a file opened with
 * Campfire is the user's own where it lies - an iCloud Drive song that may not be on this device at all, or one
 * another app is in the middle of writing. The coordinated read is what downloads the first and waits for the
 * second; an uncoordinated one simply answers nothing, and the user is told their song was skipped. Blocking for
 * as long as that takes, so not for the main thread - doubly so, since a coordinated read asked for on the main
 * thread can deadlock against a file presenter that answers on it.
 */
@OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)
internal fun NSURL.readImportedFile(budget: ImportBudget): ImportedFile? {
    val isAccessible = startAccessingSecurityScopedResource()
    return try {
        var file: ImportedFile? = null
        memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            NSFileCoordinator(filePresenter = null).coordinateReadingItemAtURL(
                url = this@readImportedFile,
                options = 0uL,
                error = error.ptr,
            ) { coordinated ->
                // The coordinator may hand over a different URL - a snapshot it made of a file that is being
                // written - and everything has to be read from that one, and before this block returns, since it
                // is only the app's for as long as the block runs.
                val url = coordinated ?: this@readImportedFile
                val size = url.path?.let { NSFileManager.defaultManager.attributesOfItemAtPath(it, error = null) }?.get(NSFileSize) as? NSNumber
                file = budget.read(name = lastPathComponent.orEmpty(), size = size?.longLongValue) { limit ->
                    // Mapped rather than loaded, so that a file whose attributes said nothing still gives its
                    // length away before any of it is copied into the heap. Longer than the limit, it only has to
                    // say so, which the budget takes one byte past the limit to mean.
                    val data = NSData.dataWithContentsOfURL(url, options = NSDataReadingMappedIfSafe, error = null)
                    if (data == null) {
                        println("Could not read \"$url\".")
                    }
                    data?.let { if (it.length.toLong() > limit) ByteArray(limit.toInt() + 1) else it.toByteArray() }
                }
            }
            // A coordination that was refused - a file that could not be downloaded, a writer that never finished -
            // says why, which is worth the log line: the import itself can only report the file as skipped.
            error.value?.let { println("Could not read \"${this@readImportedFile}\": ${it.localizedDescription}") }
        }
        file
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

/** Best effort: a file that is already gone, or that the picker moved out, is the outcome this wanted anyway. */
@OptIn(ExperimentalForeignApi::class)
private fun NSURL.deleteTemporaryFile() {
    NSFileManager.defaultManager.removeItemAtURL(this, error = null)
}

/**
 * Whether this is a copy iOS made for the app in the temporary directory - what the picker hands over when it is
 * asked for copies - rather than the user's own file somewhere else. `NSTemporaryDirectory` is reached through a
 * symbolic link (`/var` for `/private/var`), so both sides are resolved before they are compared, the way
 * `isInboxCopy` does it in `IosFileImport.kt`.
 */
private fun NSURL.isTemporaryCopy(): Boolean {
    val temporaryPath = NSURL.fileURLWithPath(NSTemporaryDirectory()).URLByResolvingSymlinksInPath?.path ?: return false
    val path = URLByResolvingSymlinksInPath?.path ?: return false
    return path.startsWith("$temporaryPath/")
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
