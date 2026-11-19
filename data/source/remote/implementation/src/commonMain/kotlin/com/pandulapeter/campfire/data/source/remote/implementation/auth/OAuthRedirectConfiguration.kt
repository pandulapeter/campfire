/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.remote.implementation.auth

/**
 * Where a consent page sends the user back to, as the app registered it with the service and with the platforms: the
 * custom scheme and host Android and iOS are called back on (`scheme://host`), and the loopback port the desktop
 * listens on. The web has no part in it, since its redirect is the page it is served from.
 *
 * The app declares it once, so that the authenticators, the shells telling a redirect from a file, the Android
 * manifest, and the iOS `Info.plist` cannot drift apart.
 *
 * @param desktopPort Fixed rather than chosen by the operating system wherever the service matches redirect URIs
 *   character for character against the ones registered with it beforehand, as Dropbox does.
 */
internal data class OAuthRedirectConfiguration(
    val scheme: String,
    val host: String,
    val desktopPort: Int,
) {

    /** The redirect URI of the platforms called back on the custom scheme. */
    val uri get() = "$scheme://$host"

    /** Whether [address] is a redirect meant for sign-in rather than, say, a file someone opened with the app. */
    fun isRedirect(address: String) = address.startsWith(uri)
}
