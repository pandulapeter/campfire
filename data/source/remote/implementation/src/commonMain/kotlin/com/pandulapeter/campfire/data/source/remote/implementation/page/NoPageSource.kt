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

import com.pandulapeter.campfire.data.model.importing.LoadedPage
import com.pandulapeter.campfire.data.source.remote.api.PageSource

/**
 * Where no page can be read: the desktop's JVM has no web view, and a browser tab may not read another site's page.
 * The platforms' definitions delegate to it.
 */
internal object NoPageSource : PageSource {

    override val canLoadPages = false

    override suspend fun load(address: String) = LoadedPage.NotRead(LoadedPage.NotRead.Reason.UNREADABLE)
}
