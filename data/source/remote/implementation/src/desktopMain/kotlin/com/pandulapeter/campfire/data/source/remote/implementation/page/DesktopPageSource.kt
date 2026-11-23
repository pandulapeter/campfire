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

/**
 * Pages read in the operating system's own web view through the app's helper ([NativeWebViewBrowser]), or none where
 * the helper or its engine is missing ([NativeWebView.isAvailable]), which is asked once, on the first use.
 */
@Single
internal class DesktopPageSource(
    private val logger: Logger,
) : PageSource {

    private val delegate by lazy {
        if (NativeWebView.isAvailable(logger)) OffscreenPageSource(newBrowser = { NativeWebViewBrowser.create() }, logger = logger) else NoPageSource
    }

    override val canLoadPages get() = delegate.canLoadPages

    override suspend fun load(address: String) = delegate.load(address)
}
