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
import androidx.compose.runtime.remember
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.paste_refused_web
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.await
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsString
import kotlin.js.Promise

/**
 * `navigator.clipboard.readText()`, which asks first: Chrome with a permission prompt, Safari and Firefox with a Paste
 * callout by the pointer. Turned down, or not there at all (an insecure context, an old browser), it is a refusal whose
 * message points at Ctrl / Cmd + V on the list, which the page hears as a `paste` event with no permission asked
 * (`PasteShortcutEffect`).
 */
@Composable
internal actual fun rememberClipboardTextReader(): ClipboardTextReader = remember {
    ClipboardTextReader {
        try {
            clipboardTextOf(readClipboardText().await<JsString>().toString())
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Throwable) {
            println("Could not read the clipboard: ${exception.message}")
            ClipboardText.Refused(Res.string.paste_refused_web)
        }
    }
}

private fun readClipboardText(): Promise<JsString> = js(
    """(navigator.clipboard && navigator.clipboard.readText ? navigator.clipboard.readText() : Promise.reject(new Error('No clipboard')))"""
)
