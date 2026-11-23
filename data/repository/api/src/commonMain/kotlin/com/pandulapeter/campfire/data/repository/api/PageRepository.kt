/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.repository.api

import com.pandulapeter.campfire.data.model.importing.LoadedPage

/** Web pages read by their address on the device, for a song shared or pasted as a link (`PageSource`). Nothing is kept. */
interface PageRepository {

    /** Whether this platform can read a page at all; where it cannot, [load] answers [LoadedPage.NotRead] at once. */
    val canLoadPages: Boolean

    /** The page at [address] once it has settled, or [LoadedPage.NotRead]; only a cancellation is thrown. */
    suspend fun load(address: String): LoadedPage
}
