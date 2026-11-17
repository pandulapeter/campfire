/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
@file:OptIn(ExperimentalWasmJsInterop::class)

package com.pandulapeter.campfire.presentation.ui.platform

import androidx.compose.runtime.Composable
import com.pandulapeter.campfire.data.model.library.ExportedFile
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny

@Composable
internal actual fun rememberFileOpener(): FileOpener = WebFileOpener

/**
 * A new tab on the file for the types a browser shows itself (PDFs, recordings, videos, pictures, and plain text), else a
 * download, which the browser hands to the system's application for it. The tab is a `blob:` address of the copy's
 * `File` in the origin private file system, read from disk rather than copied into the page; a browser that refuses the
 * tab (the press it was for too long ago, a pop-up blocker) downloads it instead.
 */
private object WebFileOpener : FileOpener {

    override suspend fun open(file: ExportedFile): Boolean {
        val blob = file.blob()
        if (!isShownByBrowsers(file.mimeType) || !openInNewTab(name = file.name, mimeType = file.mimeType, blob = blob)) {
            downloadFile(name = file.name, mimeType = file.mimeType, blob = blob)
        }
        return true
    }

    private fun isShownByBrowsers(mimeType: String) = mimeType == "application/pdf" || mimeType == "text/plain" ||
        mimeType.startsWith("audio/") || mimeType.startsWith("video/") || mimeType in PICTURE_MIME_TYPES
}

/** Whether a tab was opened; the address is let go of once the tab had time to load it. */
private fun openInNewTab(name: String, mimeType: String, blob: JsAny): Boolean = js(
    """(function () {
        var url = URL.createObjectURL(new File([blob], name, { type: mimeType }));
        var tab = window.open(url, '_blank');
        if (!tab) {
            URL.revokeObjectURL(url);
            return false;
        }
        setTimeout(function () { URL.revokeObjectURL(url); }, 60000);
        return true;
    })()"""
)
