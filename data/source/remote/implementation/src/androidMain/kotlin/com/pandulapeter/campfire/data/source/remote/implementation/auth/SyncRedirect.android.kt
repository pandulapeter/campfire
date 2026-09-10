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
 * The way in for the redirect Android delivers as an intent. Public because the activity that receives it lives in
 * `:app:android`, which is the composition root and the only place that knows about intents at all.
 *
 * @param uri The full redirect URI, `campfire://oauth?code=...`.
 */
fun onSyncRedirectReceived(uri: String) = AndroidSyncAuthenticator.onRedirectReceived(uri)

/** Whether [uri] is a redirect meant for sync rather than, say, a file someone opened with Campfire. */
fun isSyncRedirect(uri: String) = uri.startsWith("$REDIRECT_SCHEME://$REDIRECT_HOST")
