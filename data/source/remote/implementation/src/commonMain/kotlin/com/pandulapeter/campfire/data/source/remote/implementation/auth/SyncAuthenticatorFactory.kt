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

import com.pandulapeter.campfire.data.source.remote.api.SyncAuthenticator
import org.koin.core.scope.Scope

internal expect fun Scope.createSyncAuthenticator(): SyncAuthenticator

/**
 * The scheme every platform that can be redirected back to registers. Kept here rather than in each platform's own
 * file so that the four of them, the Android manifest and the iOS `Info.plist` cannot drift apart.
 */
internal const val REDIRECT_SCHEME = "campfire"
internal const val REDIRECT_HOST = "oauth"
