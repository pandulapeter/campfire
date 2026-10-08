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
import com.pandulapeter.campfire.data.model.domain.Logger
import com.pandulapeter.campfire.data.model.domain.SyncAccount
import com.pandulapeter.campfire.data.model.domain.SyncProviderId
import com.pandulapeter.campfire.data.source.remote.api.SyncAuthorizationException
import com.pandulapeter.campfire.data.source.remote.api.SyncNetworkException
import com.pandulapeter.campfire.data.source.remote.api.SyncProvider
import com.pandulapeter.campfire.data.source.remote.api.model.RemoteAuthorizationRequest
import com.pandulapeter.campfire.data.source.remote.api.model.RemoteAuthorizationResponse
import com.pandulapeter.campfire.data.source.remote.api.model.RemoteDeletion
import com.pandulapeter.campfire.data.source.remote.api.model.RemoteDocument
import com.pandulapeter.campfire.data.source.remote.api.model.RemoteFile
import com.pandulapeter.campfire.data.source.remote.api.model.RemoteListing
import com.pandulapeter.campfire.data.source.remote.api.model.RemoteWriteResult
import com.pandulapeter.campfire.data.source.remote.implementation.auth.SyncCredentialsDocument
import com.pandulapeter.campfire.data.source.remote.implementation.auth.SyncCredentialsStore
import com.pandulapeter.campfire.data.source.remote.implementation.crypto.Pkce
import com.pandulapeter.campfire.data.source.remote.implementation.crypto.dropboxContentHash
import com.pandulapeter.campfire.data.source.remote.implementation.network.HttpClientHolder
import com.pandulapeter.campfire.data.source.remote.implementation.network.exponentialBackoffSeconds
import com.pandulapeter.campfire.data.source.remote.implementation.network.toAsciiJsonString
import com.pandulapeter.campfire.data.source.remote.implementation.network.urlEncode
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerializationException
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
    private val httpClientHolder: HttpClientHolder,
    private val credentialsStore: SyncCredentialsStore,
    private val appKey: String,
    private val logger: Logger,
) : SyncProvider {

    override val id = SyncProviderId.DROPBOX

    private val tokens = DropboxTokens(
        httpClientHolder = httpClientHolder,
        credentialsStore = credentialsStore,
        appKey = appKey,
        providerId = id,
    )

    private val requests = DropboxTransport(httpClientHolder = httpClientHolder, tokens = tokens)

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
        val token = tokens.exchange(
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
                expiresAt = tokens.expiryOf(token.expiresInSeconds),
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
            withTimeoutOrNull(REVOKE_TIMEOUT_MILLIS) { tokens.accessToken() }
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
                transport { httpClientHolder.client().post(REVOKE_URL) { header("Authorization", "Bearer $accessToken") } }
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            logger.log("Could not revoke the Dropbox token: ${exception.message}")
        }
    }

    override suspend fun loadAccount(): SyncAccount? {
        val credentials = credentialsStore.load()?.takeIf { it.providerId == id.id } ?: return null
        return try {
            val account = json.decodeFromString<DropboxAccountResponse>(requests.rpc(ACCOUNT_URL, body = null))
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
            logger.log("Dropbox no longer accepts the stored credentials: ${exception.message}")
            null
        } catch (exception: Exception) {
            // Offline, or an answer that could not be read, is not "not connected": the stored name is what the app
            // knew last time, and it is still true.
            logger.log("Could not read the Dropbox account: ${exception.message}")
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
            requests.rpc(LIST_FOLDER_URL, """{"path":"","recursive":true,"include_deleted":false}""")
        )
        files += response.entries.toRemoteFiles()
        while (response.hasMore) {
            response = json.decodeFromString(requests.rpc(LIST_FOLDER_CONTINUE_URL, """{"cursor":${response.cursor.toAsciiJsonString()}}"""))
            files += response.entries.toRemoteFiles()
        }
        return RemoteListing(files = files)
    }

    override suspend fun download(kind: LibraryFileKind, name: String): RemoteDocument {
        val response = downloadResponse(remotePath(kind, name))
        response.ensureSuccessful()
        return RemoteDocument(bytes = transport { response.readRawBytes() }, revision = revisionOf(response))
    }

    override suspend fun downloadDocument(name: String): RemoteDocument? {
        val response = downloadResponse(documentPath(name))
        if (response.status == HttpStatusCode.Conflict && response.errorSummary().startsWith("path/not_found")) return null
        response.ensureSuccessful()
        return RemoteDocument(bytes = transport { response.readRawBytes() }, revision = revisionOf(response))
    }

    /**
     * A download answers with the file itself, so its metadata - the revision the next upload has to name - comes in a
     * header instead, which Dropbox also exposes to a page's script.
     */
    private fun revisionOf(response: HttpResponse) = try {
        response.headers[DOWNLOAD_RESULT_HEADER]?.let { json.decodeFromString<DropboxFileMetadata>(it) }
    } catch (exception: SerializationException) {
        null
    }?.rev ?: throw DropboxApiException(response.status.value, "the download named no revision")

    private suspend fun downloadResponse(path: String) = requests.request { accessToken ->
        httpClientHolder.client().post(DOWNLOAD_URL) {
            header("Authorization", "Bearer $accessToken")
            header("Dropbox-API-Arg", """{"path":${path.toAsciiJsonString()}}""")
        }
    }

    override fun contentHashOf(bytes: ByteArray) = dropboxContentHash(bytes)

    override suspend fun upload(
        kind: LibraryFileKind,
        name: String,
        bytes: ByteArray,
        expectedRevision: String?,
    ) = upload(path = remotePath(kind, name), bytes = bytes, expectedRevision = expectedRevision)

    override suspend fun uploadDocument(
        name: String,
        bytes: ByteArray,
        expectedRevision: String?,
    ) = upload(path = documentPath(name), bytes = bytes, expectedRevision = expectedRevision)

    private suspend fun upload(
        path: String,
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
        val response = requests.request { accessToken ->
            httpClientHolder.client().post(UPLOAD_URL) {
                header("Authorization", "Bearer $accessToken")
                header(
                    "Dropbox-API-Arg",
                    """{"path":${path.toAsciiJsonString()},"mode":$mode,"autorename":false,"mute":true}""",
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
     * [DropboxTransport.request] would give a single write.
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
                    reason.startsWith("too_many_write_operations") && attempt < DropboxTransport.MAXIMUM_RETRIES -> busy += deletion
                    else -> failures[deletion] = reason
                }
            }
            if (busy.isNotEmpty()) {
                delay(exponentialBackoffSeconds(attempt) * 1000L + Random.nextLong(DropboxTransport.RETRY_JITTER_MILLIS))
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
        var response = json.decodeFromString<DropboxDeleteBatchResponse>(requests.rpc(DELETE_BATCH_URL, body))
        val jobId = response.asyncJobId
        while (response.tag == "async_job_id" || response.tag == "in_progress") {
            delay(DELETE_BATCH_POLL_MILLIS)
            response = json.decodeFromString<DropboxDeleteBatchResponse>(
                requests.rpc(DELETE_BATCH_CHECK_URL, """{"async_job_id":${jobId.toAsciiJsonString()}}"""),
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

    /** At the top of the app folder, where [toRemoteFiles] never looks, beside the two library folders. */
    private fun documentPath(name: String) = "/$name"

    private companion object {
        const val AUTHORIZE_URL = "https://www.dropbox.com/oauth2/authorize"
        const val REVOKE_URL = "https://api.dropboxapi.com/2/auth/token/revoke"
        const val ACCOUNT_URL = "https://api.dropboxapi.com/2/users/get_current_account"
        const val LIST_FOLDER_URL = "https://api.dropboxapi.com/2/files/list_folder"
        const val LIST_FOLDER_CONTINUE_URL = "https://api.dropboxapi.com/2/files/list_folder/continue"
        const val DELETE_BATCH_URL = "https://api.dropboxapi.com/2/files/delete_batch"
        const val DELETE_BATCH_CHECK_URL = "https://api.dropboxapi.com/2/files/delete_batch/check"
        const val DOWNLOAD_URL = "https://content.dropboxapi.com/2/files/download"
        const val UPLOAD_URL = "https://content.dropboxapi.com/2/files/upload"
        const val DOWNLOAD_RESULT_HEADER = "Dropbox-API-Result"

        /** How long disconnecting waits for Dropbox, first to renew the token and then to revoke it. */
        const val REVOKE_TIMEOUT_MILLIS = 10_000L

        /** The most entries Dropbox takes in one `delete_batch`. */
        const val DELETE_BATCH_LIMIT = 1000

        /** How often a running `delete_batch` job is asked about. It removes about seven files a second. */
        const val DELETE_BATCH_POLL_MILLIS = 1_000L
    }
}
