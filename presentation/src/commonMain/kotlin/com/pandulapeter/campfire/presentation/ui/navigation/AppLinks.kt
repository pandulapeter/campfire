/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.navigation

/**
 * The app's own links, `campfire://` followed by a web address ([BrowserRoutes]), which open what the web build opens at
 * that address. The launcher shortcuts are these. `campfire://s/`, the shared link, and `campfire://oauth`, the sign-in
 * redirect, are the same scheme, which the shells tell apart first and never hand over as one of these.
 */
internal object AppLinks {

    private const val PREFIX = "campfire://"

    /** The link that opens [path]. */
    fun of(path: String) = PREFIX + path

    /**
     * The web address [link] opens, still percent-encoded, without the slashes around it and anything after a `?` or a
     * `#`, which no address of the app has; null for a link of another scheme.
     */
    fun pathOf(link: String) = link.takeIf { it.startsWith(PREFIX, ignoreCase = true) }
        ?.substring(PREFIX.length)
        ?.substringBefore('?')
        ?.substringBefore('#')
        ?.trim('/')
}
