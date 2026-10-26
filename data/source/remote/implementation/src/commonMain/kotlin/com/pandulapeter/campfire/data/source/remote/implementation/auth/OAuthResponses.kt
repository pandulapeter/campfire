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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What a token endpoint answers an exchange with. A renewal may or may not hand out a new refresh token. Dropbox also
 * names the account; a service that does not leaves [accountId] empty.
 */
@Serializable
internal data class OAuthTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresInSeconds: Long = 0,
    @SerialName("account_id") val accountId: String = "",
)

/** What a token endpoint answers a refusal with: standard OAuth, such as `invalid_grant` for a spent code. */
@Serializable
internal data class OAuthErrorResponse(
    val error: String = "",
    @SerialName("error_description") val errorDescription: String = "",
)
