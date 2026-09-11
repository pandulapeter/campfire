/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.remote.implementation.dropbox

import com.pandulapeter.campfire.data.source.local.api.SyncStateLocalSource
import com.pandulapeter.campfire.data.source.remote.api.hashing.Sha256
import com.pandulapeter.campfire.data.source.remote.api.model.redirectParameters
import com.pandulapeter.campfire.data.source.remote.implementation.auth.SyncCredentialsStore
import com.pandulapeter.campfire.data.source.remote.implementation.network.createHttpClient
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The authorization URL is the one piece of the OAuth flow that can be checked without a network: get a parameter
 * wrong and the user meets an error page on Dropbox's own site, with nothing in the app to say why.
 */
@OptIn(ExperimentalEncodingApi::class)
class DropboxAuthorizationTest {

    @Test
    fun `builds an authorization url with the parameters the service requires`() {
        val request = provider().buildAuthorizationRequest(REDIRECT_URI)
        val parameters = redirectParameters(request.authorizationUrl)
        assertTrue(request.authorizationUrl.startsWith("https://www.dropbox.com/oauth2/authorize?"))
        assertEquals(expected = APP_KEY, actual = parameters["client_id"])
        assertEquals(expected = "code", actual = parameters["response_type"])
        assertEquals(expected = "S256", actual = parameters["code_challenge_method"])
        // Without this Dropbox issues a token that expires in hours and cannot be renewed without asking again.
        assertEquals(expected = "offline", actual = parameters["token_access_type"])
        assertEquals(expected = REDIRECT_URI, actual = parameters["redirect_uri"])
    }

    /** The whole point of PKCE: what travels is the hash, and the verifier never leaves the device. */
    @Test
    fun `sends the hash of the verifier rather than the verifier`() {
        val request = provider().buildAuthorizationRequest(REDIRECT_URI)
        val challenge = redirectParameters(request.authorizationUrl)["code_challenge"]
        assertEquals(
            expected = Base64.UrlSafe.encode(Sha256.hash(request.verifier.encodeToByteArray())).trimEnd('='),
            actual = challenge,
        )
        assertTrue(request.authorizationUrl.contains(request.verifier).not())
    }

    @Test
    fun `echoes the state it generated, so that an unexpected redirect can be told apart`() {
        val request = provider().buildAuthorizationRequest(REDIRECT_URI)
        assertEquals(expected = request.state, actual = redirectParameters(request.authorizationUrl)["state"])
    }

    /** A verifier reused across attempts would let a code stolen from one of them be spent on another. */
    @Test
    fun `generates a new verifier and state every time`() {
        val first = provider().buildAuthorizationRequest(REDIRECT_URI)
        val second = provider().buildAuthorizationRequest(REDIRECT_URI)
        assertNotEquals(illegal = first.verifier, actual = second.verifier)
        assertNotEquals(illegal = first.state, actual = second.state)
    }

    /** The specification's range is 43 to 128 characters of the unreserved set. */
    @Test
    fun `generates a verifier of a length the specification allows`() {
        val verifier = provider().buildAuthorizationRequest(REDIRECT_URI).verifier
        assertTrue(verifier.length in 43..128, "The verifier is ${verifier.length} characters long.")
        assertTrue(verifier.all { it.isLetterOrDigit() || it in "-._~" }, "The verifier holds a reserved character.")
    }

    /** The desktop's copy-a-code fallback: no redirect URI at all, rather than an empty one. */
    @Test
    fun `leaves the redirect out when the platform cannot receive one`() =
        assertTrue(provider().buildAuthorizationRequest(redirectUri = null).authorizationUrl.contains("redirect_uri").not())

    private fun provider() = DropboxSyncProvider(
        httpClient = createHttpClient(),
        credentialsStore = SyncCredentialsStore(NoStorage),
        appKey = APP_KEY,
    )

    /** Building the URL touches no storage, so the test does not need any. */
    private object NoStorage : SyncStateLocalSource {
        override suspend fun loadSyncCredentials(): String? = null
        override suspend fun saveSyncCredentials(document: String?) = Unit
        override suspend fun loadSyncIndex(): String? = null
        override suspend fun saveSyncIndex(document: String?) = Unit
    }

    private companion object {
        const val APP_KEY = "test-app-key"
        const val REDIRECT_URI = "http://127.0.0.1:53682"
    }
}
