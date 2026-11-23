/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.remote.api

import com.pandulapeter.campfire.data.model.importing.LoadedPage

/**
 * Reads a web page by its address in a real browser engine kept out of sight, so a page drawn by its own scripts reads
 * as the user would have seen it, and answers its text; nothing of the page is shown, and nothing is kept once it has
 * answered (no cookie or cache the app reads again).
 *
 * Only a platform with a web view of its own has one; [canLoadPages] tells a caller whether asking is any use, so the
 * user can be told at once rather than after a wait.
 */
interface PageSource {

    /** Whether this platform can read a page at all: Android and iOS can; the desktop's JVM and a browser tab cannot. */
    val canLoadPages: Boolean

    /**
     * The page at [address] once it has settled, or [LoadedPage.NotRead]. Only a public `https` host is asked, on the
     * first address and on every page it is sent on to (never the local network, since the address comes from another
     * app); the whole read is bounded in time, and a page whose text stops changing for a moment once loaded is taken as
     * it is then. A page that is a bot check, or has no text, is [LoadedPage.NotRead]. Never throws but cancellation;
     * where [canLoadPages] is false, answers [LoadedPage.NotRead] without a request.
     */
    suspend fun load(address: String): LoadedPage
}
