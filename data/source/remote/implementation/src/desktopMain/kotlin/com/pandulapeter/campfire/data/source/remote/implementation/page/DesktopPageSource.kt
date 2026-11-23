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
import com.pandulapeter.campfire.data.source.remote.api.PageSource
import org.koin.core.annotation.Single
import kotlin.concurrent.thread

/**
 * Pages read in the operating system's own web view through the app's helper ([NativeWebViewBrowser]), or none where
 * the helper or its engine is missing ([NativeWebView.isAvailable]). Loading the helper links the system's web engine,
 * which can take a noticeable fraction of a second on a cold launch, so the check starts on a background thread as the
 * source is created, and the first read that comes before it ends waits for what is left of it.
 */
@Single
internal class DesktopPageSource(
    private val logger: Logger,
) : PageSource {

    private val delegate by lazy {
        if (NativeWebView.isAvailable(logger)) OffscreenPageSource(newBrowser = { NativeWebViewBrowser.create() }, logger = logger) else NoPageSource
    }

    init {
        // A plain daemon thread, since no scope would own a coroutine started here. Anything isAvailable lets through is
        // caught so it does not print as this thread's uncaught exception: the lazy then stays uninitialized, and the
        // first read runs it again and meets the error itself.
        thread(isDaemon = true, name = "Campfire web view check") { runCatching { delegate } }
    }

    override val canLoadPages get() = delegate.canLoadPages

    override suspend fun load(address: String) = delegate.load(address)
}
