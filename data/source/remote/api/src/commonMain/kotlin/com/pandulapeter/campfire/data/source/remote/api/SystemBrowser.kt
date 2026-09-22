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

/**
 * Opening a URL in the user's own browser, which on the desktop is something the application shell knows how to do
 * and the data layer does not: `java.awt.Desktop` is right about half the Linux sessions there are, and what the
 * other half needs is the operating system's own command. The app already has that one implementation, for the
 * links in Settings, and this is how the consent page reaches it.
 *
 * Declared here next to [SyncAuthenticator] for the same reason that one is: the platform's half of the
 * authorization flow belongs in the contracts, and each platform provides its own. Only the desktop needs it - the
 * other three authenticators open their own browser through an API of their own.
 */
fun interface SystemBrowser {

    /** @return Whether anything took the URL. False where no browser could be opened at all. */
    fun open(url: String): Boolean
}
