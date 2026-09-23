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

import com.pandulapeter.campfire.data.model.domain.LibraryFileKind
import com.pandulapeter.campfire.data.model.domain.SyncAccount
import com.pandulapeter.campfire.data.model.domain.SyncProviderId
import com.pandulapeter.campfire.data.source.remote.api.SyncAuthorizationException
import com.pandulapeter.campfire.data.source.remote.api.SyncNetworkException
import com.pandulapeter.campfire.data.source.remote.api.SyncProvider
import com.pandulapeter.campfire.data.source.remote.api.SyncRemoteStorageFullException
import com.pandulapeter.campfire.data.source.remote.api.model.RemoteAuthorizationRequest
import com.pandulapeter.campfire.data.source.remote.api.model.RemoteAuthorizationResponse
import com.pandulapeter.campfire.data.source.remote.api.model.RemoteDeletion
import com.pandulapeter.campfire.data.source.remote.api.model.RemoteFile
import com.pandulapeter.campfire.data.source.remote.api.model.RemoteListing
import com.pandulapeter.campfire.data.source.remote.api.model.RemoteWriteResult
import com.pandulapeter.campfire.data.source.remote.implementation.auth.SyncCredentialsDocument
import com.pandulapeter.campfire.data.source.remote.implementation.auth.SyncCredentialsStore
import com.pandulapeter.campfire.data.source.remote.implementation.crypto.Pkce
import com.pandulapeter.campfire.data.source.remote.implementation.crypto.dropboxContentHash
import com.pandulapeter.campfire.data.source.remote.implementation.network.toAsciiJsonString
import com.pandulapeter.campfire.data.source.remote.implementation.network.urlEncode
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlin.math.min
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Dropbox, seen through [SyncProvider].
 *
 * The app is registered for the "app folder" permission, so everything here addresses paths inside
 * `Apps/Campfire Sync` and the rest of the user's Dropbox is not merely off limits - it is invisible. That folder is
 * a real one the user can open, which is the same promise the local library makes: plain files, in a place they can
 * look at, in a format that is not Campfire's to own.
 *
 * Authorization is OAuth 2.0 with PKCE and no client secret, which is what lets this work with no server of
 * Campfire's own anywhere in the picture.
 */
internal class DropboxSyncProvider(
    private val httpClient: HttpClient,
    private val credentialsStore: SyncCredentialsStore,
    private val appKey: String,
) : SyncProvider {

    override val id = SyncProviderId.DROPBOX

    override suspend fun isConnected() = credentialsStore.load()?.takeIf { it.providerId == id.id }?.refreshToken?.isNotEmpty() == true

    // Authorization

    override fun buildAuthorizationRequest(redirectUri: String?): RemoteAuthorizationRequest {
        val verifier = Pkce.createVerifier()
        val state = Pkce.createState()
        val url = buildString {
            append("$AUTHORIZE_URL?client_id=${appKey.urlEncode()}")
            append("&response_type=code")
            append("&code_challenge=${Pkce.createChallenge(verifier).urlEncode()}")
            append("&code_challenge_method=S256")
            // Without this the token expires in four hours and there is no way to renew it without asking again.
            append("&token_access_type=offline")
            append("&state=${state.urlEncode()}")
            if (redirectUri != null) {
                append("&redirect_uri=${redirectUri.urlEncode()}")
            }
        }
        return RemoteAuthorizationRequest(authorizationUrl = url, redirectUri = redirectUri, state = state, verifier = verifier)
    }

    override suspend fun completeAuthorization(
        response: RemoteAuthorizationResponse,
        verifier: String,
        redirectUri: String?,
    ): SyncAccount {
        val token = exchange(
            buildString {
                append("grant_type=authorization_code")
                append("&code=${response.code.urlEncode()}")
                append("&client_id=${appKey.urlEncode()}")
                append("&code_verifier=${verifier.urlEncode()}")
                if (redirectUri != null) {
                    append("&redirect_uri=${redirectUri.urlEncode()}")
                }
            }
        )
        val refreshToken = token.refreshToken
            ?: throw SyncAuthorizationException("Dropbox did not return a refresh token.")
        credentialsStore.save(
            SyncCredentialsDocument(
                providerId = id.id,
                accessToken = token.accessToken,
                refreshToken = refreshToken,
                expiresAt = expiryOf(token.expiresInSeconds),
                accountId = token.accountId,
            )
        )
        // Written a second time with the name on it, so that a failure to read the account still leaves a usable
        // connection rather than tokens nobody can put a face to.
        return loadAccount() ?: SyncAccount(providerId = id, id = token.accountId, displayName = token.accountId, email = null)
    }

    override suspend fun forgetStoredCredentials() = credentialsStore.save(null)

    override suspend fun disconnect() {
        // Dropbox revokes the whole grant through a valid access token, and a stored one is expired after four idle
        // hours, so it is renewed first if needed. Nothing connected, or no answer in time, leaves nothing to revoke
        // with; the time limit is what keeps a dead network from keeping the user connected.
        val accessToken = try {
            withTimeoutOrNull(REVOKE_TIMEOUT_MILLIS) { accessToken() }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            null
        }
        credentialsStore.save(null)
        if (accessToken.isNullOrEmpty()) return
        // Best effort: the local credentials are already gone, and a token that cannot be revoked simply expires.
        try {
            withTimeoutOrNull(REVOKE_TIMEOUT_MILLIS) {
                transport { httpClient.post(REVOKE_URL) { header("Authorization", "Bearer $accessToken") } }
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            println("Could not revoke the Dropbox token: ${exception.message}")
        }
    }

    override suspend fun loadAccount(): SyncAccount? {
        val credentials = credentialsStore.load()?.takeIf { it.providerId == id.id } ?: return null
        return try {
            val account = json.decodeFromString<DropboxAccountResponse>(rpc(ACCOUNT_URL, body = null))
            credentialsStore.update { current ->
                current?.copy(
                    accountId = account.accountId,
                    displayName = account.name.displayName,
                    email = account.email,
                ) to Unit
            }
            SyncAccount(
                providerId = id,
                id = account.accountId,
                displayName = account.name.displayName.ifEmpty { account.email },
                email = account.email.takeIf { it.isNotEmpty() },
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: SyncAuthorizationException) {
            // Refused or revoked, which is the one answer that means the credentials are no longer good for anything.
            println("Dropbox no longer accepts the stored credentials: ${exception.message}")
            null
        } catch (exception: Exception) {
            // Offline, or an answer that could not be read, is not "not connected": the stored name is what the app
            // knew last time, and it is still true.
            println("Could not read the Dropbox account: ${exception.message}")
            storedAccountOf(credentials)
        }
    }

    override suspend fun storedAccount() = credentialsStore.load()
        ?.takeIf { it.providerId == id.id && it.refreshToken.isNotEmpty() }
        ?.let(::storedAccountOf)

    /**
     * An account whose name was never read - the first read after the authorization failed too - is still a working
     * connection, so it goes by its id until a read succeeds, which is also the name the authorization gave it.
     */
    private fun storedAccountOf(credentials: SyncCredentialsDocument): SyncAccount? {
        val name = credentials.displayName.ifEmpty { credentials.email }.ifEmpty { credentials.accountId }.ifEmpty { return null }
        return SyncAccount(
            providerId = id,
            id = credentials.accountId,
            displayName = name,
            email = credentials.email.takeIf(String::isNotEmpty),
        )
    }

    // Files

    override suspend fun list(): RemoteListing {
        val files = mutableListOf<RemoteFile>()
        var response = json.decodeFromString<DropboxListFolderResponse>(
            rpc(LIST_FOLDER_URL, """{"path":"","recursive":true,"include_deleted":false}""")
        )
        files += response.entries.toRemoteFiles()
        while (response.hasMore) {
            response = json.decodeFromString(rpc(LIST_FOLDER_CONTINUE_URL, """{"cursor":${response.cursor.toAsciiJsonString()}}"""))
            files += response.entries.toRemoteFiles()
        }
        return RemoteListing(files = files)
    }

    override suspend fun download(kind: LibraryFileKind, name: String): ByteArray {
        val response = request { accessToken ->
            httpClient.post(DOWNLOAD_URL) {
                header("Authorization", "Bearer $accessToken")
                header("Dropbox-API-Arg", """{"path":${remotePath(kind, name).toAsciiJsonString()}}""")
            }
        }
        response.ensureSuccessful()
        return transport { response.readRawBytes() }
    }

    override fun contentHashOf(bytes: ByteArray) = dropboxContentHash(bytes)

    override suspend fun upload(
        kind: LibraryFileKind,
        name: String,
        bytes: ByteArray,
        expectedRevision: String?,
    ): RemoteWriteResult {
        // "update" refuses the write if the file has moved on since the caller last saw it, and "add" refuses it if
        // the file exists at all. Autorename is off: a name Dropbox invented would be a song nobody asked for.
        val mode = if (expectedRevision == null) {
            """{".tag":"add"}"""
        } else {
            """{".tag":"update","update":${expectedRevision.toAsciiJsonString()}}"""
        }
        val response = request { accessToken ->
            httpClient.post(UPLOAD_URL) {
                header("Authorization", "Bearer $accessToken")
                header(
                    "Dropbox-API-Arg",
                    """{"path":${remotePath(kind, name).toAsciiJsonString()},"mode":$mode,"autorename":false,"mute":true}""",
                )
                contentType(ContentType.Application.OctetStream)
                setBody(bytes)
            }
        }
        if (response.status == HttpStatusCode.Conflict) {
            val summary = response.errorSummary()
            if (summary.startsWith("path/conflict")) return RemoteWriteResult.Conflict
        }
        response.ensureSuccessful()
        val metadata = try {
            json.decodeFromString<DropboxFileMetadata>(response.bodyAsText())
        } catch (exception: SerializationException) {
            // The write went through and only its receipt is unreadable, which the contract files under "cannot tell".
            throw SyncNetworkException("Dropbox's answer to an upload could not be read.", exception)
        }
        return RemoteWriteResult.Written(metadata.rev)
    }

    override suspend fun delete(deletions: List<RemoteDeletion>): Map<RemoteDeletion, String> =
        deletions.chunked(DELETE_BATCH_LIMIT).fold(emptyMap()) { failures, batch -> failures + deleteBatch(batch) }

    /**
     * One `delete_batch` job. Dropbox takes a lock on the whole app folder for every write, so single deletions sent
     * side by side are mostly answered with `too_many_write_operations` and crawl through the back-off at about one
     * file a second; a job takes the lock once and removes about seven a second. It is not atomic - the files go one
     * after another while it runs - so it shortens the time the folder spends half deleted rather than removing it.
     * Its entries can still be turned away as busy, and those go again in a job of their own after the same wait
     * [request] would give a single write.
     */
    private suspend fun deleteBatch(deletions: List<RemoteDeletion>): Map<RemoteDeletion, String> {
        val failures = mutableMapOf<RemoteDeletion, String>()
        var pending = deletions
        var attempt = 0
        while (pending.isNotEmpty()) {
            val entries = awaitDeleteBatch(pending)
            val busy = mutableListOf<RemoteDeletion>()
            pending.forEachIndexed { position, deletion ->
                val entry = entries.getOrNull(position)
                val reason = when {
                    entry == null -> "no answer for this entry"
                    entry.tag == "success" -> return@forEachIndexed
                    else -> entry.failure?.tagPath().orEmpty()
                }
                when {
                    // Already gone is the outcome that was asked for, and a file that changed since is one the next
                    // run will see and bring back rather than destroy.
                    reason.startsWith("path_lookup/not_found") || reason.startsWith("path_write/conflict") -> Unit
                    reason.startsWith("too_many_write_operations") && attempt < MAXIMUM_RETRIES -> busy += deletion
                    else -> failures[deletion] = reason
                }
            }
            if (busy.isNotEmpty()) {
                delay(min(DEFAULT_RETRY_SECONDS shl attempt, MAXIMUM_RETRY_SECONDS) * 1000L + Random.nextLong(RETRY_JITTER_MILLIS))
                attempt++
            }
            pending = busy
        }
        return failures
    }

    /**
     * Starts a job and waits for it to finish. The entries of the answer are in the order the files were sent. A job
     * refused as a whole is answered as every one of its entries refused for the same reason, which lets the caller
     * treat a busy folder the same way whichever of the two Dropbox chose to say it with.
     */
    private suspend fun awaitDeleteBatch(deletions: List<RemoteDeletion>): List<DropboxDeleteBatchEntry> {
        val body = deletions.joinToString(prefix = """{"entries":[""", postfix = "]}") { deletion ->
            buildString {
                append("""{"path":${remotePath(deletion.kind, deletion.name).toAsciiJsonString()}""")
                deletion.expectedRevision?.let { revision -> append(""","parent_rev":${revision.toAsciiJsonString()}""") }
                append("}")
            }
        }
        var response = json.decodeFromString<DropboxDeleteBatchResponse>(rpc(DELETE_BATCH_URL, body))
        val jobId = response.asyncJobId
        while (response.tag == "async_job_id" || response.tag == "in_progress") {
            delay(DELETE_BATCH_POLL_MILLIS)
            response = json.decodeFromString<DropboxDeleteBatchResponse>(
                rpc(DELETE_BATCH_CHECK_URL, """{"async_job_id":${jobId.toAsciiJsonString()}}"""),
            )
        }
        if (response.tag == "complete") return response.entries
        val reason = JsonPrimitive(response.failed?.tagPath() ?: response.tag)
        return deletions.map { DropboxDeleteBatchEntry(tag = "failure", failure = JsonObject(mapOf(".tag" to reason))) }
    }

    private fun List<DropboxEntry>.toRemoteFiles() = mapNotNull { entry ->
        if (entry.tag != "file") return@mapNotNull null
        val kind = LibraryFileKind.entries.firstOrNull { entry.pathLower.startsWith("/${it.id}/") } ?: return@mapNotNull null
        // Only the two library folders, one level deep: anything the user put deeper into the app folder is theirs.
        if (entry.pathLower.count { it == '/' } != 2) return@mapNotNull null
        RemoteFile(
            kind = kind,
            name = entry.name,
            revision = entry.rev,
            contentHash = entry.contentHash,
            size = entry.size,
        )
    }

    private fun remotePath(kind: LibraryFileKind, name: String) = "/${kind.id}/$name"

    // Requests

    /** A Dropbox "RPC" call: JSON in, JSON out, everything above the transport reported in the body. */
    private suspend fun rpc(url: String, body: String?): String {
        val response = request { accessToken ->
            httpClient.post(url) {
                header("Authorization", "Bearer $accessToken")
                if (body != null) {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
            }
        }
        response.ensureSuccessful()
        return transport { response.bodyAsText() }
    }

    /**
     * One call, retried while Dropbox asks it to slow down.
     *
     * A first sync of a whole library is a few hundred calls in quick succession, so being rate limited is the
     * expected answer rather than an exceptional one - and giving up on the run because of it would mean a library
     * that can never finish its first sync. Writes are limited separately: several of them landing in one folder at
     * once are answered with a 409 whose summary says `too_many_write_operations`, which Dropbox documents as "back off
     * and retry" rather than as a failure of that file. Dropbox says how long to wait in a `retry_after` field of the
     * body or in `Retry-After`, and that is waited as given. Where it says nothing - typically its own trouble, a 5xx -
     * the wait doubles from two seconds up to half a minute, so that an outage of a minute or so is sat out rather than
     * ending the run. The jitter is there because several transfers are in flight at once and would otherwise all
     * come back at the same moment and be limited again together.
     *
     * A 401 is answered once with a refresh before it is believed. Whether the stored token is still good is worked
     * out from the device's clock, and a clock that was wrong when the token was issued keeps saying "fresh" long after
     * Dropbox has stopped accepting it - while the refresh token that would fix that is perfectly good. [block] is
     * handed the token it sends, so that the one refused is known and only that one is renewed: the transfers of a run
     * refused together share one renewal. A second 401 is the real refusal.
     */
    private suspend fun request(block: suspend (accessToken: String) -> HttpResponse): HttpResponse {
        var attempt = 0
        var refusedToken: String? = null
        while (true) {
            var token = ""
            // The token is asked for inside transport, like the call: a renewal whose answer cannot be decoded must
            // stay a network failure of the run rather than become one of this file.
            val response = transport {
                token = accessToken(refused = refusedToken)
                block(token)
            }
            if (response.status == HttpStatusCode.Unauthorized && refusedToken == null) {
                refusedToken = token
                continue
            }
            val retryAfterMillis = response.retryAfterMillis(attempt)
            if (retryAfterMillis == null || attempt >= MAXIMUM_RETRIES) return response
            attempt++
            delay(retryAfterMillis + Random.nextLong(RETRY_JITTER_MILLIS))
        }
    }

    /** Null when the answer is one to act on rather than to wait out. */
    private suspend fun HttpResponse.retryAfterMillis(attempt: Int): Long? = when {
        status == HttpStatusCode.TooManyRequests ||
            status.value >= 500 ||
            (status == HttpStatusCode.Conflict && errorSummary().contains("too_many_write_operations")) ->
            (retryAfterSecondsInBody() ?: headers["Retry-After"]?.toLongOrNull()
                ?: min(DEFAULT_RETRY_SECONDS shl attempt, MAXIMUM_RETRY_SECONDS)) * 1000L

        else -> null
    }

    private suspend fun HttpResponse.retryAfterSecondsInBody() = try {
        json.decodeFromString<DropboxRateLimitResponse>(bodyAsText()).error.retryAfter
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        null
    }

    /**
     * Anything the transport throws - no route to the host, a dropped connection, a timeout - is one thing to the
     * user: the service could not be reached, and trying again later is worth doing. Only the call itself is
     * wrapped, so that a body Campfire cannot make sense of stays the programming error it is. The browser engine
     * reports a failed `fetch` as a `kotlin.Error`, not an `Exception`, so the last branch takes any `Throwable`:
     * everything `block` can throw that is not a cancellation is the call failing.
     *
     * A cancellation is the one exception that says nothing about the network. Stopping a run or giving up on a
     * consent page resumes every suspended request with one, and it has to leave here as what it is: the engine and
     * the repository both answer a stopped run differently from a failed one, and can only do so if they are told.
     */
    private suspend fun <T> transport(block: suspend () -> T): T = try {
        block()
    } catch (exception: CancellationException) {
        // Thrown on again only while this coroutine really is cancelled. A cancellation that reaches a coroutine
        // nobody cancelled belongs to something underneath - a client that was closed, a timeout surfacing as one -
        // and passed on it would end a transfer of the engine's without a word, as though the user had stopped it.
        currentCoroutineContext().ensureActive()
        throw SyncNetworkException(exception.message ?: "Dropbox could not be reached.", exception)
    } catch (exception: SyncAuthorizationException) {
        throw exception
    } catch (exception: SyncNetworkException) {
        throw exception
    } catch (exception: DropboxApiException) {
        throw exception
    } catch (throwable: Throwable) {
        throw SyncNetworkException(throwable.message ?: "Dropbox could not be reached.", throwable)
    }

    private suspend fun HttpResponse.ensureSuccessful() {
        if (status.isSuccess()) return
        val summary = errorSummary()
        throw when {
            status == HttpStatusCode.Unauthorized -> SyncAuthorizationException("Dropbox refused the token: $summary")
            // Dropbox says 429 for rate limiting and 5xx for its own trouble; both are worth trying again later.
            status == HttpStatusCode.TooManyRequests || status.value >= 500 -> SyncNetworkException("Dropbox is busy: ${status.value}")
            // A full account, which Dropbox reports per write: "path/insufficient_space/..".
            status == HttpStatusCode.Conflict && summary.contains("insufficient_space") ->
                SyncRemoteStorageFullException("Dropbox is full: $summary")
            else -> DropboxApiException(status.value, summary)
        }
    }

    private suspend fun HttpResponse.errorSummary() = try {
        json.decodeFromString<DropboxErrorResponse>(bodyAsText()).errorSummary
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        ""
    }

    /**
     * The stored access token, renewed first if it is about to expire, or regardless when it is the one [refused]
     * says Dropbox has already turned down. The whole check and renewal happen inside one [SyncCredentialsStore.update],
     * so that the several requests of a sync run cannot each start a refresh of their own and have the last one to
     * finish overwrite the tokens the others are using. A token that was refused is renewed only while it is still the
     * stored one: six transfers refused at once make one renewal, and the other five use its result.
     */
    private suspend fun accessToken(refused: String? = null): String = credentialsStore.update { credentials ->
        if (credentials == null || credentials.providerId != id.id) {
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

    private suspend fun exchange(form: String): DropboxTokenResponse {
        val response = transport {
            httpClient.post(TOKEN_URL) {
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

    private fun expiryOf(expiresInSeconds: Long) =
        if (expiresInSeconds <= 0) 0 else Clock.System.now().toEpochMilliseconds() + expiresInSeconds * 1000

    private companion object {
        const val AUTHORIZE_URL = "https://www.dropbox.com/oauth2/authorize"
        const val TOKEN_URL = "https://api.dropboxapi.com/oauth2/token"
        const val REVOKE_URL = "https://api.dropboxapi.com/2/auth/token/revoke"
        const val ACCOUNT_URL = "https://api.dropboxapi.com/2/users/get_current_account"
        const val LIST_FOLDER_URL = "https://api.dropboxapi.com/2/files/list_folder"
        const val LIST_FOLDER_CONTINUE_URL = "https://api.dropboxapi.com/2/files/list_folder/continue"
        const val DELETE_BATCH_URL = "https://api.dropboxapi.com/2/files/delete_batch"
        const val DELETE_BATCH_CHECK_URL = "https://api.dropboxapi.com/2/files/delete_batch/check"
        const val DOWNLOAD_URL = "https://content.dropboxapi.com/2/files/download"
        const val UPLOAD_URL = "https://content.dropboxapi.com/2/files/upload"

        /** How long disconnecting waits for Dropbox, first to renew the token and then to revoke it. */
        const val REVOKE_TIMEOUT_MILLIS = 10_000L

        /** The most entries Dropbox takes in one `delete_batch`. */
        const val DELETE_BATCH_LIMIT = 1000

        /** How often a running `delete_batch` job is asked about. It removes about seven files a second. */
        const val DELETE_BATCH_POLL_MILLIS = 1_000L

        /** Tokens are renewed slightly early, so that one does not expire between the check and the request. */
        const val EXPIRY_MARGIN_MILLIS = 60_000L

        /** Six waits of 2, 4, 8, 16, 32 and 32 seconds: about a minute and a half of patience. */
        const val MAXIMUM_RETRIES = 6

        /** What to wait first when Dropbox asks to slow down without saying for how long, doubled on every attempt. */
        const val DEFAULT_RETRY_SECONDS = 2L
        const val MAXIMUM_RETRY_SECONDS = 32L
        const val RETRY_JITTER_MILLIS = 500L

        val json = Json { ignoreUnknownKeys = true }
    }
}

/** A call Dropbox answered, with something other than success. */
internal class DropboxApiException(val statusCode: Int, val errorSummary: String) :
    Exception("Dropbox answered $statusCode: $errorSummary")
