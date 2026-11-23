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

import com.pandulapeter.campfire.data.model.Logger
import java.io.File

/**
 * The desktop's web view helper: a small JNI library of the app's own (`campfirewebview`, built from `native/` beside
 * this source set by `:app:desktop`) over the operating system's web view, WebKit on macOS, WebView2 on Windows, and
 * WebKitGTK on Linux, so no browser engine is bundled. The packages carry it as an app resource, the folder the
 * launcher names in `compose.application.resources.dir`; a build without a toolchain for it carries none, and pages are
 * then not read.
 *
 * Every function returns at once: the helper moves the work onto its engine's thread and answers through the view's
 * [NativeWebViewEvents]. A view is a handle that stays valid until [close], after which nothing is called on its events.
 */
internal object NativeWebView {

    private var isLoaded: Boolean? = null

    /**
     * Whether the helper is there and its engine can run on this machine (a WebView2 runtime installed on Windows,
     * WebKitGTK and a display on Linux). Loads the library on the first call; never throws.
     */
    @Synchronized
    fun isAvailable(logger: Logger): Boolean = isLoaded ?: load(logger).also { isLoaded = it }

    private fun load(logger: Logger): Boolean {
        val library = System.getProperty("compose.application.resources.dir")
            ?.let { File(it, System.mapLibraryName(LIBRARY_NAME)) }
            ?.takeIf { it.isFile }
        if (library == null) {
            logger.log("No web view helper in this build, so pages are not read.")
            return false
        }
        return try {
            System.load(library.absolutePath)
            isEngineAvailable().also { if (!it) logger.log("The web view helper found no engine, so pages are not read.") }
        } catch (error: LinkageError) {
            logger.log("Could not load the web view helper: ${error.message}")
            false
        }
    }

    @JvmStatic
    external fun isEngineAvailable(): Boolean

    /**
     * A new view laid out at [width] × [height], out of sight, with nothing stored beyond its own life; [dataDirectory]
     * is where an engine that needs a folder for its browser processes (WebView2) may keep them. Its events go to [events].
     */
    @JvmStatic
    external fun create(events: NativeWebViewEvents, width: Int, height: Int, dataDirectory: String): Long

    @JvmStatic
    external fun open(view: Long, address: String)

    /** Runs [script] in the page; what it answers reaches [NativeWebViewEvents.onScriptResult]. */
    @JvmStatic
    external fun evaluate(view: Long, script: String)

    @JvmStatic
    external fun close(view: Long)

    private const val LIBRARY_NAME = "campfirewebview"
}
