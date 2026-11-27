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

/**
 * Whether [uri] is a redirect meant for sync rather than, say, a file someone opened with Campfire. Public because the
 * activity that receives it lives in `:app:android`. Only a redirect on this build's [scheme] is one: the other build's
 * is never the answer to a sign-in this one started.
 */
fun isSyncRedirect(uri: String, scheme: SyncRedirectScheme) = scheme.redirect.isRedirect(uri)
