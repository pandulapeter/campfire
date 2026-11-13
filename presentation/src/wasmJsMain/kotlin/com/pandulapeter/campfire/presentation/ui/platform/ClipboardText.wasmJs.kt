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
import kotlin.js.JsAny
import kotlin.js.Promise

/**
 * `navigator.clipboard.read()`, for the HTML beside the text ([pastedText]), or `readText()` where a browser has no
 * `read()`. Either asks first, and the same way: Chrome with its clipboard permission prompt, Safari and Firefox with a
 * Paste callout by the pointer. Turned down, or not there at all (an insecure context, an old browser), it is a refusal
 * whose message points at Ctrl / Cmd + V on the list, which the page hears as a `paste` event with no permission asked
 * (`PasteShortcutEffect`). A `read()` turned down is not followed by a `readText()`, which would ask a second time.
 */
@Composable
internal actual fun rememberClipboardTextReader(): ClipboardTextReader = remember {
    ClipboardTextReader {
        try {
            val contents = readClipboard().await<JsAny>()
            clipboardTextOf(text = textIn(contents), html = htmlIn(contents))
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Throwable) {
            println("Could not read the clipboard: ${exception.message}")
            ClipboardText.Refused(Res.string.paste_refused_web)
        }
    }
}

private fun readClipboard(): Promise<JsAny> = js(
    """(function () {
        var clipboard = navigator.clipboard;
        if (!clipboard) return Promise.reject(new Error('No clipboard'));
        if (!clipboard.read) {
            if (!clipboard.readText) return Promise.reject(new Error('No clipboard'));
            return clipboard.readText().then(function (text) { return { text: text, html: null }; });
        }
        return clipboard.read().then(function (items) {
            function contentOf(type) {
                for (var i = 0; i < items.length; i++) {
                    if (items[i].types.indexOf(type) >= 0) {
                        return items[i].getType(type).then(function (blob) { return blob.text(); }).catch(function () { return null; });
                    }
                }
                return Promise.resolve(null);
            }
            return Promise.all([contentOf('text/plain'), contentOf('text/html')]).then(function (contents) {
                return { text: contents[0], html: contents[1] };
            });
        });
    })()"""
)

private fun textIn(contents: JsAny): String? = js("contents.text")

private fun htmlIn(contents: JsAny): String? = js("contents.html")
