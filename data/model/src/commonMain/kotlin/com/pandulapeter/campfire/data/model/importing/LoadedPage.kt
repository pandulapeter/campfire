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

    /** No page to convert, for [reason], which decides what the user is told to do about it. */
    data class NotRead(val reason: Reason) : LoadedPage {

        enum class Reason {

            /**
             * The page never arrived: no network, its host not found or not answering, or nothing of it drawn before
             * the time limit. Trying again once the connection is back may well read it.
             */
            UNREACHABLE,

            /**
             * The page arrived but is nothing to read: a bot check standing in for it, a page with no text, an error
             * page, an address that is not a public `https` one, or a platform or setting that reads no page. Trying
             * again changes nothing; opening it in the browser and sharing or copying the page itself does.
             */
            UNREADABLE,
        }
    }
}
