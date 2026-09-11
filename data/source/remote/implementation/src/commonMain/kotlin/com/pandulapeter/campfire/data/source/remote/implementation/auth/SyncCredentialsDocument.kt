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

import kotlinx.serialization.Serializable

/**
 * The on-disk shape of `sync-credentials.json`. Every field is defaulted, so a document written by an older version
 * keeps loading; the provider is stored by its stable `id` rather than by ordinal.
 *
 * [pending] is the authorization that has been started but not finished. It is written down rather than kept in
 * memory because the web platform navigates away from the running app to ask for consent, and the PKCE verifier has
 * to still be there when the app starts up again.
 */
@Serializable
internal data class SyncCredentialsDocument(
    val providerId: String = "",
    val accessToken: String = "",
    val refreshToken: String = "",
    /** Milliseconds since the epoch, 0 when the provider did not say and the token is assumed not to expire. */
    val expiresAt: Long = 0,
    val accountId: String = "",
    val displayName: String = "",
    val email: String = "",
    val pending: Pending? = null,
) {

    @Serializable
    data class Pending(
        val providerId: String = "",
        val state: String = "",
        val verifier: String = "",
        val redirectUri: String = "",
    )
}
