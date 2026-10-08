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

import com.pandulapeter.campfire.data.model.domain.LibraryFileKind
import com.pandulapeter.campfire.data.source.remote.api.SyncAuthorizationException
import com.pandulapeter.campfire.data.source.remote.api.SyncNetworkException
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.http.parseQueryString
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * How the provider keeps the stored connection's tokens good: renewed before they expire, renewed once when Dropbox
 * refuses one, and a refusal of the renewal itself told apart from the token endpoint being busy.
 */
class DropboxTokensTest {

    @Test
    fun `an expired token is renewed before the first request rather than after a refusal`() = runTest {
        val requests = mutableListOf<String>()
        var refreshForm = ""
        val provider = dropboxProvider(storage = ConnectedStorage(expiresAt = 0)) { request ->
            if (request.url.toString() == TOKEN_URL) {
                requests += "token"
                refreshForm = (request.body as TextContent).text
                respondJson("""{"access_token":"renewed","expires_in":14400}""")
            } else {
                requests += request.headers["Authorization"].orEmpty()
                respondJson(EMPTY_LISTING)
            }
        }
        provider.list()
        assertEquals(expected = listOf("token", "Bearer renewed"), actual = requests)
        val form = parseQueryString(refreshForm)
        assertEquals("refresh_token", form["grant_type"])
        assertEquals("refresh", form["refresh_token"])
    }

    /** Dropbox may hand out a new refresh token, and keeping the old one would end the connection silently. */
    @Test
    fun `a renewal that carries a new refresh token stores it`() = runTest {
        val storage = ConnectedStorage(expiresAt = 0)
        val provider = dropboxProvider(storage = storage) { request ->
            if (request.url.toString() == TOKEN_URL) {
                respondJson("""{"access_token":"renewed","refresh_token":"new refresh","expires_in":14400}""")
            } else {
                respondJson(EMPTY_LISTING)
            }
        }
        provider.list()
        assertEquals("renewed", storage.document?.accessToken)
        assertEquals("new refresh", storage.document?.refreshToken)
    }

    @Test
    fun `a renewal without a refresh token keeps the old one`() = runTest {
        val storage = ConnectedStorage(expiresAt = 0)
        val provider = dropboxProvider(storage = storage) { request ->
            if (request.url.toString() == TOKEN_URL) {
                respondJson("""{"access_token":"renewed","expires_in":14400}""")
            } else {
                respondJson(EMPTY_LISTING)
            }
        }
        provider.list()
        assertEquals("renewed", storage.document?.accessToken)
        assertEquals("refresh", storage.document?.refreshToken)
    }

    /** Being told to wait says nothing about the credentials, and must not send the user off to connect again. */
    @Test
    fun `a busy token endpoint is the service not being reached rather than a refusal`() = runTest {
        for (status in listOf(HttpStatusCode.TooManyRequests, HttpStatusCode.ServiceUnavailable)) {
            val storage = ConnectedStorage(expiresAt = 0)
            val provider = dropboxProvider(storage = storage) { request ->
                if (request.url.toString() == TOKEN_URL) respond(content = "", status = status) else respondJson(EMPTY_LISTING)
            }
            assertFailsWith<SyncNetworkException> { provider.list() }
            assertEquals("refresh", storage.document?.refreshToken)
        }
    }

    @Test
    fun `a token exchange the browser could not send is the service not being reached`() = runTest {
        val provider = dropboxProvider(storage = ConnectedStorage(expiresAt = 0)) { request ->
            if (request.url.toString() == TOKEN_URL) throw Error("Fail to fetch")
            respondJson(EMPTY_LISTING)
        }
        assertFailsWith<SyncNetworkException> { provider.list() }
    }

    /** A token the device's clock still believes in can be one Dropbox has stopped accepting. */
    @Test
    fun `refreshes the token once when dropbox refuses it`() = runTest {
        val authorizations = mutableListOf<String?>()
        var tokenRequestCount = 0
        val provider = dropboxProvider { request ->
            if (request.url.toString() == TOKEN_URL) {
                tokenRequestCount++
                respondJson("""{"access_token":"renewed","expires_in":14400}""")
            } else {
                authorizations += request.headers["Authorization"]
                if (authorizations.size == 1) {
                    respondJson("""{"error_summary":"expired_access_token/..."}""", HttpStatusCode.Unauthorized)
                } else {
                    respondJson(EMPTY_LISTING)
                }
            }
        }
        provider.list()
        assertEquals(expected = 1, actual = tokenRequestCount)
        assertEquals(expected = listOf<String?>("Bearer access", "Bearer renewed"), actual = authorizations)
    }

    @Test
    fun `several requests refused at once renew the token once`() = runTest {
        var tokenRequestCount = 0
        val provider = dropboxProvider { request ->
            when {
                request.url.toString() == TOKEN_URL -> {
                    tokenRequestCount++
                    respondJson("""{"access_token":"renewed","expires_in":14400}""")
                }

                request.headers["Authorization"] == "Bearer renewed" -> respond(
                    content = "song",
                    status = HttpStatusCode.OK,
                    headers = headersOf("Dropbox-API-Result", """{"rev":"r1"}"""),
                )
                else -> respondJson("""{"error_summary":"expired_access_token/..."}""", HttpStatusCode.Unauthorized)
            }
        }
        coroutineScope {
            repeat(6) { launch { assertEquals("song", provider.download(LibraryFileKind.SONG, "song_$it.cho").bytes.decodeToString()) } }
        }
        assertEquals(expected = 1, actual = tokenRequestCount)
    }

    @Test
    fun `believes a refusal of a token it has just refreshed`() = runTest {
        var tokenRequestCount = 0
        val provider = dropboxProvider { request ->
            if (request.url.toString() == TOKEN_URL) {
                tokenRequestCount++
                respondJson("""{"access_token":"renewed","expires_in":14400}""")
            } else {
                respondJson("""{"error_summary":"invalid_access_token/..."}""", HttpStatusCode.Unauthorized)
            }
        }
        assertFailsWith<SyncAuthorizationException> { provider.list() }
        assertEquals(expected = 1, actual = tokenRequestCount)
    }

    @Test
    fun `reports why the token endpoint refused a refresh`() = runTest {
        val provider = dropboxProvider { request ->
            if (request.url.toString() == TOKEN_URL) {
                respondJson("""{"error":"invalid_grant","error_description":"refresh token is invalid or revoked"}""", HttpStatusCode.BadRequest)
            } else {
                respondJson("""{"error_summary":"expired_access_token/..."}""", HttpStatusCode.Unauthorized)
            }
        }
        val exception = assertFailsWith<SyncAuthorizationException> { provider.list() }
        assertEquals(
            expected = "Dropbox refused the authorization: 400 invalid_grant: refresh token is invalid or revoked",
            actual = exception.message,
        )
    }
}
