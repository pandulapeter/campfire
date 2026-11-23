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

/**
 * One page in a platform's web view, drawn out of sight, for [OffscreenPageSource] to look at until it settles. Each
 * method is called from any thread, and an implementation moves to the one its view needs. Every navigation the page
 * makes, its own first address and every redirect included, is refused unless it is a public `https` one.
 */
internal interface OffscreenBrowser {

    /** Starts loading [address], which is already a public `https` one; called once, and never suspends for the load. */
    suspend fun open(address: String)

    /** The page as it is drawn now, and whether it has finished loading or failed to. */
    suspend fun look(): PageLook

    /** Lets go of the view and whatever it holds; called once, however the read ended, cancelled included. */
    suspend fun close()
}

/**
 * @param snapshot The page's text, title, and address, or null where they could not be read yet (no document so far).
 * @param isLoaded Whether the page itself has finished loading; what its scripts draw after that is waited for apart.
 * @param hasFailed Whether the page could not be loaded at all (no answer, a refused address), so there is nothing to
 *   wait for.
 */
internal data class PageLook(
    val snapshot: PageSnapshot?,
    val isLoaded: Boolean,
    val hasFailed: Boolean,
)
