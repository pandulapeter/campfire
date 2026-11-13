/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable

@Composable
internal actual fun rememberClipboardTextReader(): ClipboardTextReader = remember { ClipboardTextReader { clipboardTextOf(awtClipboardText()) } }

/**
 * The system clipboard's text, its HTML's chord sheet where it has one ([pastedText]), or null where it holds none. Read
 * off the main thread, since on X11 it is a round trip to whichever application owns the selection. The clipboard is busy while another application is writing it, which
 * AWT says with an [IllegalStateException]: it is asked once more a moment later rather than taken for empty.
 */
internal suspend fun awtClipboardText(): String? = withContext(Dispatchers.IO) {
    try {
        readClipboardText()
    } catch (_: IllegalStateException) {
        delay(BUSY_CLIPBOARD_RETRY_MILLIS)
        try {
            readClipboardText()
        } catch (exception: IllegalStateException) {
            println("Could not read the clipboard: ${exception.message}")
            null
        }
    }
}

private fun readClipboardText() = Toolkit.getDefaultToolkit().systemClipboard.getContents(null)?.pastedText()

/**
 * The text a paste or a drop of this makes a song of. Its HTML (the selection's, else the whole document's) is an extra:
 * a source that offers the flavour and then fails to convert it still leaves its plain text.
 */
internal fun Transferable.pastedText(): String? = pastedText(
    text = if (isDataFlavorSupported(DataFlavor.stringFlavor)) getTransferData(DataFlavor.stringFlavor) as? String else null,
    html = try {
        listOf(DataFlavor.selectionHtmlFlavor, DataFlavor.allHtmlFlavor).firstOrNull(::isDataFlavorSupported)?.let { getTransferData(it) as? String }
    } catch (exception: Exception) {
        println("Could not read the clipboard's HTML: ${exception.message}")
        null
    },
)

private const val BUSY_CLIPBOARD_RETRY_MILLIS = 100L
