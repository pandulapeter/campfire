/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.remote.implementation.network

import com.pandulapeter.campfire.data.source.remote.implementation.CAMPFIRE_VERSION

/**
 * How every request names the app, in the form MusicBrainz asks of its clients — the application, its version and a
 * way to reach the people behind it — which it throttles hardest when a client does not. It names the app and
 * nothing about the user.
 */
internal val USER_AGENT = userAgent(CAMPFIRE_VERSION)

internal fun userAgent(version: String) = "Campfire/$version ( $CONTACT_URL )"

private const val CONTACT_URL = "https://github.com/pandulapeter/campfire"
