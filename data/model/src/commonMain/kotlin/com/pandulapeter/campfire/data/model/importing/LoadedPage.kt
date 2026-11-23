/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.model.importing

/** What reading a web page by its address came to: its text as it was drawn, or nothing worth converting. */
sealed interface LoadedPage {

    /**
     * The page as a reader saw it once it settled: its body's `innerText`, its title (null where it has none), and the
     * address it ended up at after its redirects.
     */
    data class Read(val text: String, val title: String?, val address: String) : LoadedPage

    /**
     * No page to convert: the address is not a public `https` one, the page did not load in time, failed, came back
     * empty, or is a bot check standing in for the page. Which of these is not told apart, since the user is told the
     * same for each: open it in the browser and share or copy the page itself.
     */
    data object NotRead : LoadedPage
}
