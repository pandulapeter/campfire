package com.pandulapeter.campfire

import com.pandulapeter.campfire.data.model.domain.ExportedFile
import com.pandulapeter.campfire.data.model.domain.ImportedFile
import com.pandulapeter.campfire.presentation.ui.platform.FilePicker
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.writeToURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.popoverPresentationController
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
    private val viewController: () -> UIViewController
) : FilePicker {

    // UIKit keeps only a weak reference to a delegate, so the one that is in flight is held here.
    private var delegate: NSObject? = null

    override suspend fun pickFiles(): List<ImportedFile> = suspendCancellableCoroutine { continuation ->
        // Everything, not just plain text: ".cho" is not a type iOS knows, so a narrower list would grey the songs
        // out in the picker. What is not a song is skipped by the import and reported afterwards.
        val controller = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeData, UTTypeZIP), asCopy = true)
        controller.allowsMultipleSelection = true
        present(controller) { urls -> continuation.resume(urls.mapNotNull { it.readImportedFile() }) }
    }

    override suspend fun saveFile(file: ExportedFile): Boolean = suspendCancellableCoroutine { continuation ->
        // The picker exports a file that already exists, so the bytes go to a temporary one first.
        val url = NSURL.fileURLWithPath(NSTemporaryDirectory() + file.name)
        if (!file.bytes.toNSData().writeToURL(url, atomically = true)) {
            continuation.resume(false)
        } else {
            present(UIDocumentPickerViewController(forExportingURLs = listOf(url))) { urls -> continuation.resume(urls.isNotEmpty()) }
        }
    }

    override val canShare = true

    override suspend fun shareFile(file: ExportedFile): Boolean = suspendCancellableCoroutine { continuation ->
        val url = NSURL.fileURLWithPath(NSTemporaryDirectory() + file.name)
        if (!file.bytes.toNSData().writeToURL(url, atomically = true)) {
            continuation.resume(false)
        } else {
            val controller = UIActivityViewController(activityItems = listOf(url), applicationActivities = null)
            // An iPad presents this as a popover, which needs something to point at; the whole view will do.
            val host = viewController()
            controller.popoverPresentationController?.sourceView = host.view
            host.presentViewController(controller, animated = true, completion = null)
            continuation.resume(true)
        }
    }

    private fun present(controller: UIDocumentPickerViewController, onFinished: (List<NSURL>) -> Unit) {
        val pickerDelegate = DocumentPickerDelegate { urls ->
            delegate = null
            onFinished(urls)
        }
        delegate = pickerDelegate
        controller.delegate = pickerDelegate
        viewController().presentViewController(controller, animated = true, completion = null)
    }

}

/**
 * Null when the file cannot be read, so that one bad file does not lose the ones next to it.
 *
 * The copies the picker hands over live in the app's own container, but a URL that arrives from another app is
 * security scoped, so the read happens inside the access it grants.
 */
internal fun NSURL.readImportedFile(): ImportedFile? {
    val isAccessible = startAccessingSecurityScopedResource()
    return try {
        NSData.dataWithContentsOfURL(this)?.let { ImportedFile(name = lastPathComponent.orEmpty(), bytes = it.toByteArray()) }
    } catch (exception: Exception) {
        println("Could not read \"$this\": ${exception.message}")
        null
    } finally {
        if (isAccessible) {
            stopAccessingSecurityScopedResource()
        }
    }
}

private class DocumentPickerDelegate(
    private val onFinished: (List<NSURL>) -> Unit
) : NSObject(), UIDocumentPickerDelegateProtocol {

    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) =
        onFinished(didPickDocumentsAtURLs.filterIsInstance<NSURL>())

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) = onFinished(emptyList())
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray() = ByteArray(length.toInt()).also { bytes ->
    if (bytes.isNotEmpty()) {
        bytes.usePinned { memcpy(it.addressOf(0), this.bytes, length) }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toNSData() = if (isEmpty()) {
    NSData()
} else {
    usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }
}
