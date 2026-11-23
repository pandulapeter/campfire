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

import com.pandulapeter.campfire.data.source.remote.implementation.network.isPublicHttpsAddress
import kotlinx.coroutines.CompletableDeferred

/**
 * What one page's view in the desktop's web view helper tells the app, and what it asks of it. The helper calls these
 * methods by name from the engine's own thread (the AppKit main thread, the WebView2 thread, or the GTK thread), never
 * the caller's, so every field is volatile and nothing here blocks or touches a view; their names and signatures are
 * part of the helper's contract (`native/`), and `proguard-rules.pro` keeps them.
 */
internal class NativeWebViewEvents {

    @Volatile
    var isLoaded = false
        private set

    @Volatile
    var hasFailed = false
        private set

    @Volatile
    var isUnreachable = false
        private set

    @Volatile
    private var scriptResult: CompletableDeferred<String?>? = null

    /** The deferred the next [onScriptResult] completes, set before the script is handed to the helper. */
    fun expectScriptResult() = CompletableDeferred<String?>().also { scriptResult = it }

    /**
     * Whether the view may go to [address]: only a public `https` one, and inside a frame also `about:`, `data:`, or
     * `blob:`, which go nowhere. A helper that cannot tell a frame's navigation from the page's asks with [isMainFrame]
     * false.
     */
    fun allowsNavigation(address: String, isMainFrame: Boolean) =
        isPublicHttpsAddress(address) || !isMainFrame && address.substringBefore(':').lowercase() in FRAME_SCHEMES

    /** Whether the view may fetch [address] for the page (where the engine asks): no picture, and only what [allowsNavigation] a frame. */
    fun allowsResource(address: String, isImage: Boolean) = !isImage && allowsNavigation(address = address, isMainFrame = false)

    fun onLoadStarted() {
        isLoaded = false
    }

    fun onLoadFinished() {
        isLoaded = true
    }

    /** The page's own load failed with the engine's error [code] of [domain], read by [NativeLoadFailure.of]. */
    fun onLoadFailed(domain: String, code: Int) {
        when (NativeLoadFailure.of(domain = domain, code = code)) {
            NativeLoadFailure.NONE -> Unit
            NativeLoadFailure.UNREACHABLE -> {
                isUnreachable = true
                hasFailed = true
            }
            NativeLoadFailure.FAILED -> hasFailed = true
        }
    }

    /** What the last script answered: its string, as the engine hands it over, or null where it threw or gave none. */
    fun onScriptResult(result: String?) {
        scriptResult?.complete(result)
    }
}

/** What a frame inside the page may load besides a public `https` address: nothing that leaves the device. */
private val FRAME_SCHEMES = setOf("about", "data", "blob")
