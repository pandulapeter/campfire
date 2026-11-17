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

import com.pandulapeter.campfire.data.model.library.ExportedContent
import com.pandulapeter.campfire.data.model.library.ExportedFile
import com.pandulapeter.campfire.presentation.ui.platform.FileOpener
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Foundation.writeToURL
import platform.QuickLook.QLPreviewController
import platform.QuickLook.QLPreviewControllerDataSourceProtocol
import platform.QuickLook.QLPreviewItemProtocol
import platform.UIKit.UIViewController
import platform.darwin.NSInteger
import platform.darwin.NSObject

/**
 * Quick Look over everything on screen: it shows PDFs, documents, recordings, videos, and pictures itself, and its share
 * button offers every app that takes the file, which is how a type it cannot show (a Guitar Pro file) is opened. In the
 * app module for the reason [IosFilePicker] is. Presented from the topmost presented controller, since UIKit refuses a
 * presentation over a controller that already presents one. Always true: Quick Look comes up for any type.
 *
 * @param viewController The controller hosting the Compose UI.
 */
internal class IosFileOpener(
    private val viewController: () -> UIViewController,
) : FileOpener {

    /** The controller keeps only a weak reference to its data source, so the one shown is held here until the next. */
    private var shownDataSource: PreviewDataSource? = null

    override suspend fun open(file: ExportedFile): Boolean {
        val url = when (val content = file.content) {
            is ExportedContent.TemporaryFile -> NSURL.fileURLWithPath(content.path)
            is ExportedContent.Bytes -> withContext(Dispatchers.IO) { writtenToTemporaryFile(file.name, content.bytes) } ?: return false
        }
        return withContext(Dispatchers.Main) {
            var host = viewController()
            while (true) host = host.presentedViewController?.takeUnless { it.isBeingDismissed() } ?: break
            val source = PreviewDataSource(PreviewItem(url = url, title = file.name))
            shownDataSource = source
            host.presentViewController(QLPreviewController().apply { dataSource = source }, animated = true, completion = null)
            true
        }
    }
}

@OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)
private fun writtenToTemporaryFile(name: String, bytes: ByteArray): NSURL? {
    val url = NSURL.fileURLWithPath(NSTemporaryDirectory() + name)
    val data = if (bytes.isEmpty()) NSData() else bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
    return url.takeIf { data.writeToURL(it, atomically = true) }
}

private class PreviewDataSource(private val item: PreviewItem) : NSObject(), QLPreviewControllerDataSourceProtocol {

    override fun numberOfPreviewItemsInPreviewController(controller: QLPreviewController): NSInteger = 1

    override fun previewController(controller: QLPreviewController, previewItemAtIndex: NSInteger): QLPreviewItemProtocol = item
}

/** The file under the name the song shows it by, which Quick Look titles it with. */
private class PreviewItem(private val url: NSURL, private val title: String) : NSObject(), QLPreviewItemProtocol {

    override fun previewItemURL(): NSURL = url

    override fun previewItemTitle(): String = title
}
