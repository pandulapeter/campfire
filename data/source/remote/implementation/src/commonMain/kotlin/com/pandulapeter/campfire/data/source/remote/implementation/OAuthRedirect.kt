/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.remote.implementation

import com.pandulapeter.campfire.data.source.remote.implementation.auth.OAuthRedirectConfiguration

/**
 * Campfire's redirect, as registered with Dropbox (`campfire://oauth` and `http://127.0.0.1:53682`), declared by the
 * Android manifest's sign-in filter and by the iOS `Info.plist`'s URL type, whose values have to stay the same. A value
 * rather than only a Koin definition because the shells read it too, and an iOS cold start opened by a URL asks before
 * the graph exists. Android's debug build is called back on a scheme of its own instead ([SyncRedirectScheme]).
 */
internal val CAMPFIRE_OAUTH_REDIRECT = OAuthRedirectConfiguration(scheme = "campfire", host = "oauth", desktopPort = 53682)
