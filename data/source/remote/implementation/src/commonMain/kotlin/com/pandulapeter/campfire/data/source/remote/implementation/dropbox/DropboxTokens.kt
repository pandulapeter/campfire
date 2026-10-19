/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
@file:OptIn(ExperimentalAtomicApi::class, ExperimentalTime::class)

package com.pandulapeter.campfire.data.source.remote.implementation.dropbox

import com.pandulapeter.campfire.data.model.sync.SyncProviderId
import com.pandulapeter.campfire.data.source.remote.api.sync.SyncAuthorizationException
import com.pandulapeter.campfire.data.source.remote.api.sync.SyncNetworkException
import com.pandulapeter.campfire.data.source.remote.implementation.auth.SyncCredentialsDocument
import com.pandulapeter.campfire.data.source.remote.implementation.auth.SyncCredentialsStore
import com.pandulapeter.campfire.data.source.remote.implementation.network.HttpClientHolder
import com.pandulapeter.campfire.data.source.remote.implementation.network.urlEncode
import io.ktor.client.plugins.timeout
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** The tokens of the stored Dropbox connection: exchanging an authorization or a refresh token for them, and renewing them. */
internal class DropboxTokens(
    private val httpClientHolder: HttpClientHolder,
    private val credentialsStore: SyncCredentialsStore,
    private val appKey: String,
    private val providerId: SyncProviderId,
) {

    /** The last renewal that could not reach Dropbox, written only under the credentials lock, see [accessToken]. */
    private val lastFailedRenewal = AtomicReference<FailedRenewal?>(null)

    /**
     * The stored access token, renewed first if it is about to expire, or regardless when it is the one [refused]
     * says Dropbox has already turned down. The whole check and renewal happen inside one [SyncCredentialsStore.update],
     * so that the several requests of a sync run cannot each start a refresh of their own and have the last one to
     * finish overwrite the tokens the others are using. A token that was refused is renewed only while it is still the
     * stored one: six transfers refused at once make one renewal, and the other five use its result.
     *
     * A renewal is shared both ways. One that succeeds is shared because the callers that waited for the lock find its
     * token stored; one that could not reach Dropbox because they fail with it rather than each starting an exchange of
     * their own against the same stalled endpoint. The callers of a run therefore cost one renewal per retry round
     * rather than one each, and a token endpoint that never answers ends the run in the time one caller would take. A
     * refusal is not shared (each waiter's own exchange is answered at once), and neither is a cancellation, so the
     * callers waiting behind a cancelled renewal renew for themselves.
     */
    suspend fun accessToken(refused: String? = null): String {
        // Read before waiting for the lock: a failure newer than this one happened while this caller waited.
        val seen = lastFailedRenewal.load()
        return credentialsStore.update { credentials ->
            if (credentials == null || credentials.providerId != providerId.id) {
                throw SyncAuthorizationException("Dropbox is not connected.")
            }
            if (credentials.accessToken != refused && hasUsableAccessToken(credentials)) {
                return@update credentials to credentials.accessToken
            }
            val failed = lastFailedRenewal.load()
            if (failed != null && failed !== seen && failed.refreshToken == credentials.refreshToken) {
                // A new instance with the same cause, so that DropboxTransport.request gives it the same retry limit.
                throw SyncNetworkException(failed.failure.message ?: "Dropbox could not be asked for a token.", failed.failure.cause)
            }
            val token = try {
                renew(credentials.refreshToken)
            } catch (exception: SyncNetworkException) {
                lastFailedRenewal.store(FailedRenewal(credentials.refreshToken, exception))
                throw exception
            }
            lastFailedRenewal.store(null)
            credentials.copy(
                accessToken = token.accessToken,
                expiresAt = expiryOf(token.expiresInSeconds),
                // Dropbox may hand out a new refresh token, and keeping the old one would end the connection silently.
                refreshToken = token.refreshToken ?: credentials.refreshToken,
            ) to token.accessToken
        }
    }

    /** Whether the access token of [credentials] is there and not about to expire. */
    fun hasUsableAccessToken(credentials: SyncCredentialsDocument) =
        credentials.accessToken.isNotEmpty() &&
            Clock.System.now().toEpochMilliseconds() < credentials.expiresAt - EXPIRY_MARGIN_MILLIS

    /** Exchanges [refreshToken] for a new access token. Stores nothing: what becomes of the answer is the caller's. */
    suspend fun renew(refreshToken: String) =
        exchange("grant_type=refresh_token&refresh_token=${refreshToken.urlEncode()}&client_id=${appKey.urlEncode()}")

    suspend fun exchange(form: String): DropboxTokenResponse {
        val response = transport {
            httpClientHolder.client().post(TOKEN_URL) {
                // A few hundred bytes on every engine, so a bound on the whole exchange cuts short nothing that could
                // finish, and it bounds how long a renewal holds the credentials lock.
                timeout { requestTimeoutMillis = TOKEN_TIMEOUT_MILLIS }
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(form)
            }
        }
        if (!response.status.isSuccess()) {
            // Being told to wait, or the service having trouble of its own, says nothing about the credentials, and
            // reporting it as a refusal would send the user off to connect an account that is perfectly fine. An answer
            // from something between the device and Dropbox - a proxy's 403 or 407, a captive portal's redirect - is
            // never about the credentials either, and every refusal OAuth defines is a 400 or a 401.
            throw when (response.status) {
                HttpStatusCode.BadRequest, HttpStatusCode.Unauthorized ->
                    SyncAuthorizationException("Dropbox refused the authorization: ${response.status.value} ${response.oAuthError()}")

                else -> SyncNetworkException("Dropbox could not be asked for a token: ${response.status.value} ${response.oAuthError()}")
            }
        }
        val token = decodeAnswer(DropboxTokenResponse.serializer(), transport { response.bodyAsText() })
        // Stored, an empty token would make every following call a refusal, and the connection one to make again.
        if (token.accessToken.isEmpty()) throw SyncNetworkException("Dropbox's answer named no access token.")
        return token
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

    /** A renewal that could not reach Dropbox, kept so that the callers who waited for it fail with it, see [accessToken]. */
    private class FailedRenewal(val refreshToken: String, val failure: SyncNetworkException)

    private companion object {
        const val TOKEN_URL = "https://api.dropboxapi.com/oauth2/token"

        /** The bound on one token request as a whole, renewal and authorization code exchange alike. */
        const val TOKEN_TIMEOUT_MILLIS = 30_000L

        /** Tokens are renewed slightly early, so that one does not expire between the check and the request. */
        const val EXPIRY_MARGIN_MILLIS = 60_000L
    }
}
