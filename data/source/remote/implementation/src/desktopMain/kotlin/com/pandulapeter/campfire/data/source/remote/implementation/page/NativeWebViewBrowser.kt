/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.remote.implementation.page

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File

/**
 * A page in the desktop's web view helper ([NativeWebView]), laid out at a desktop window's size and never shown. What
 * each engine refuses is in its source under `native/` and in this module's `CLAUDE.md`; the rules are the phones':
 * scripts on, pictures off where the engine can refuse them, nothing kept, and every navigation refused off a public
 * `https` host ([NativeWebViewEvents.allowsNavigation]).
 */
internal class NativeWebViewBrowser private constructor(
    private val view: Long,
    private val events: NativeWebViewEvents,
) : OffscreenBrowser {

    override suspend fun open(address: String) = NativeWebView.open(view, address)

    override suspend fun look(): PageLook {
        val answer = events.expectScriptResult()
        NativeWebView.evaluate(view, PAGE_SNAPSHOT_SCRIPT)
        return PageLook(
            snapshot = PageSnapshot.decode(scriptString(answer.await())),
            isLoaded = events.isLoaded,
            hasFailed = events.hasFailed,
            isUnreachable = events.isUnreachable,
        )
    }

    override suspend fun close() = NativeWebView.close(view)

    companion object {

        /** A laptop's browser window, which is what a page pasted on a desktop was read at. */
        private const val WIDTH = 1280
        private const val HEIGHT = 800

        private val isWindows = System.getProperty("os.name").orEmpty().lowercase().startsWith("windows")

        fun create(): NativeWebViewBrowser {
            val events = NativeWebViewEvents()
            // WebView2 keeps its browser processes' files in a folder of its own even for an InPrivate view, which holds
            // nothing of a page once it is closed; a temporary one keeps them out of the library's folder.
            val dataDirectory = File(System.getProperty("java.io.tmpdir"), "Campfire WebView2").absolutePath
            return NativeWebViewBrowser(NativeWebView.create(events, WIDTH, HEIGHT, dataDirectory), events)
        }

        /** WebView2 answers a script with its result as JSON, a string as a JSON literal; WebKit hands the string itself. */
        private fun scriptString(answer: String?): String? = if (isWindows) {
            answer?.takeIf { it != "null" }?.let { literal ->
                try {
                    Json.decodeFromString<String>(literal)
                } catch (_: SerializationException) {
                    null
                } catch (_: IllegalArgumentException) {
                    null
                }
            }
        } else {
            answer
        }
    }
}
