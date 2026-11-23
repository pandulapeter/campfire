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

import android.content.Context
import com.pandulapeter.campfire.data.model.Logger
import com.pandulapeter.campfire.data.source.remote.api.PageSource
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

/** Pages read in a `WebView` that is never attached to a window ([WebViewBrowser]). */
@Single
internal class AndroidPageSource(
    @Provided context: Context,
    logger: Logger,
) : PageSource by OffscreenPageSource(newBrowser = { WebViewBrowser.create(context.applicationContext) }, logger = logger)
