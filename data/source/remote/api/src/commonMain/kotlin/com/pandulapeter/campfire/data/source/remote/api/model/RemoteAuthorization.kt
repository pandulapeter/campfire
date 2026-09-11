/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.remote.api.model

/**
 * The start of an authorization: where to send the user, and what has to survive until they come back.
 *
 * @param state The random value echoed back in the redirect, which is what makes a redirect that was not asked for
 *   detectable.
 * @param verifier The PKCE code verifier. It has to outlive a full page reload on the web, so it is written down
 *   rather than kept in memory - see `SyncStateLocalSource`.
 */
data class RemoteAuthorizationRequest(
    val authorizationUrl: String,
    val redirectUri: String?,
    val state: String,
    val verifier: String,
)

/** What comes back from the browser, already picked apart. */
data class RemoteAuthorizationResponse(
    val code: String,
    val state: String?,
)
