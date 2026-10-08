/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
@file:OptIn(ExperimentalTime::class)

package com.pandulapeter.campfire.data.source.remote.implementation.dropbox

import com.pandulapeter.campfire.data.model.domain.SyncProviderId
import com.pandulapeter.campfire.data.source.remote.api.SyncAuthorizationException
import com.pandulapeter.campfire.data.source.remote.api.SyncNetworkException
import com.pandulapeter.campfire.data.source.remote.implementation.auth.SyncCredentialsStore
import com.pandulapeter.campfire.data.source.remote.implementation.network.HttpClientHolder
import com.pandulapeter.campfire.data.source.remote.implementation.network.urlEncode
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** The tokens of the stored Dropbox connection: exchanging an authorization or a refresh token for them, and renewing them. */
internal class DropboxTokens(
    private val httpClientHolder: HttpClientHolder,
    private val credentialsStore: SyncCredentialsStore,
    private val appKey: String,
    private val providerId: SyncProviderId,
) {

    /**
     * The stored access token, renewed first if it is about to expire, or regardless when it is the one [refused]
     * says Dropbox has already turned down. The whole check and renewal happen inside one [SyncCredentialsStore.update],
     * so that the several requests of a sync run cannot each start a refresh of their own and have the last one to
     * finish overwrite the tokens the others are using. A token that was refused is renewed only while it is still the
     * stored one: six transfers refused at once make one renewal, and the other five use its result.
     */
    suspend fun accessToken(refused: String? = null): String = credentialsStore.update { credentials ->
        if (credentials == null || credentials.providerId != providerId.id) {
            throw SyncAuthorizationException("Dropbox is not connected.")
        }
        if (credentials.accessToken != refused &&
            credentials.accessToken.isNotEmpty() &&
            Clock.System.now().toEpochMilliseconds() < credentials.expiresAt - EXPIRY_MARGIN_MILLIS
        ) {
            return@update credentials to credentials.accessToken
        }
        val token = exchange("grant_type=refresh_token&refresh_token=${credentials.refreshToken.urlEncode()}&client_id=${appKey.urlEncode()}")
        credentials.copy(
            accessToken = token.accessToken,
            expiresAt = expiryOf(token.expiresInSeconds),
            // Dropbox may hand out a new refresh token, and keeping the old one would end the connection silently.
            refreshToken = token.refreshToken ?: credentials.refreshToken,
        ) to token.accessToken
    }

    suspend fun exchange(form: String): DropboxTokenResponse {
        val response = transport {
            httpClientHolder.client().post(TOKEN_URL) {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(form)
            }
        }
        if (!response.status.isSuccess()) {
            // Being told to wait, or the service having trouble of its own, says nothing about the credentials, and
            // reporting it as a refusal would send the user off to connect an account that is perfectly fine.
            if (response.status == HttpStatusCode.TooManyRequests || response.status.value >= 500) {
                throw SyncNetworkException("Dropbox is busy: ${response.status.value}")
            }
            throw SyncAuthorizationException("Dropbox refused the authorization: ${response.status.value} ${response.oAuthError()}")
        }
        return json.decodeFromString(response.bodyAsText())
    }

    /** The token endpoint speaks standard OAuth rather than the API's own `error_summary`. */
    private suspend fun HttpResponse.oAuthError(): String {
        val error = try {
            json.decodeFromString<DropboxOAuthErrorResponse>(bodyAsText())
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            DropboxOAuthErrorResponse()
        }
        return if (error.error.isEmpty() && error.errorDescription.isEmpty()) {
            errorSummary()
        } else {
            "${error.error}: ${error.errorDescription}"
        }
    }

    fun expiryOf(expiresInSeconds: Long) =
        if (expiresInSeconds <= 0) 0 else Clock.System.now().toEpochMilliseconds() + expiresInSeconds * 1000

    private companion object {
        const val TOKEN_URL = "https://api.dropboxapi.com/oauth2/token"

        /** Tokens are renewed slightly early, so that one does not expire between the check and the request. */
        const val EXPIRY_MARGIN_MILLIS = 60_000L
    }
}
