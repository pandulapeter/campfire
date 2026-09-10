@file:OptIn(ExperimentalTime::class)

package com.pandulapeter.campfire.data.source.remote.implementation.dropbox

import com.pandulapeter.campfire.data.model.domain.LibraryFileKind
import com.pandulapeter.campfire.data.model.domain.SyncAccount
import com.pandulapeter.campfire.data.model.domain.SyncProviderId
import com.pandulapeter.campfire.data.source.remote.api.SyncAuthorizationException
import com.pandulapeter.campfire.data.source.remote.api.SyncNetworkException
import com.pandulapeter.campfire.data.source.remote.api.SyncProvider
import com.pandulapeter.campfire.data.source.remote.api.model.RemoteAuthorizationRequest
import com.pandulapeter.campfire.data.source.remote.api.model.RemoteAuthorizationResponse
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
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.serialization.json.Json

/**
 * Dropbox, seen through [SyncProvider].
 *
 * The app is registered for the "app folder" permission, so everything here addresses paths inside
 * `Apps/Campfire` and the rest of the user's Dropbox is not merely off limits - it is invisible. That folder is a
 * real one the user can open, which is the same promise the local library makes: plain files, in a place they can
 * look at, in a format that is not Campfire's to own.
 *
 * Authorization is OAuth 2.0 with PKCE and no client secret, which is what lets this work with no server of
 * Campfire's own anywhere in the picture.
 */
internal class DropboxSyncProvider(
    private val httpClient: HttpClient,
    private val credentialsStore: SyncCredentialsStore,
    private val appKey: String
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
        redirectUri: String?
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
                accountId = token.accountId
            )
        )
        // Written a second time with the name on it, so that a failure to read the account still leaves a usable
        // connection rather than tokens nobody can put a face to.
        return loadAccount() ?: SyncAccount(providerId = id, displayName = token.accountId, email = null)
    }

    override suspend fun disconnect() {
        val credentials = credentialsStore.load()
        credentialsStore.save(null)
        // Best effort: the local credentials are already gone, and a token that cannot be revoked simply expires.
        credentials?.accessToken?.takeIf { it.isNotEmpty() }?.let { accessToken ->
            try {
                httpClient.post(REVOKE_URL) { header("Authorization", "Bearer $accessToken") }
            } catch (exception: Exception) {
                println("Could not revoke the Dropbox token: ${exception.message}")
            }
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
                    email = account.email
                ) to Unit
            }
            SyncAccount(
                providerId = id,
                displayName = account.name.displayName.ifEmpty { account.email },
                email = account.email.takeIf { it.isNotEmpty() }
            )
        } catch (exception: SyncNetworkException) {
            // Offline is not "not connected": the stored name is what the app knew last time, and it is still true.
            credentials.displayName.takeIf { it.isNotEmpty() }?.let {
                SyncAccount(providerId = id, displayName = it, email = credentials.email.takeIf(String::isNotEmpty))
            }
        }
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
        val token = accessToken()
        val response = transport {
            httpClient.post(DOWNLOAD_URL) {
                header("Authorization", "Bearer $token")
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
        expectedRevision: String?
    ): RemoteWriteResult {
        // "update" refuses the write if the file has moved on since the caller last saw it, and "add" refuses it if
        // the file exists at all. Autorename is off: a name Dropbox invented would be a song nobody asked for.
        val mode = if (expectedRevision == null) {
            """{".tag":"add"}"""
        } else {
            """{".tag":"update","update":${expectedRevision.toAsciiJsonString()}}"""
        }
        val token = accessToken()
        val response = transport {
            httpClient.post(UPLOAD_URL) {
                header("Authorization", "Bearer $token")
                header(
                    "Dropbox-API-Arg",
                    """{"path":${remotePath(kind, name).toAsciiJsonString()},"mode":$mode,"autorename":false,"mute":true}"""
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
        return RemoteWriteResult.Written(json.decodeFromString<DropboxFileMetadata>(response.bodyAsText()).rev)
    }

    override suspend fun delete(kind: LibraryFileKind, name: String, expectedRevision: String?) {
        val body = buildString {
            append("""{"path":${remotePath(kind, name).toAsciiJsonString()}""")
            if (expectedRevision != null) {
                append(""","parent_rev":${expectedRevision.toAsciiJsonString()}""")
            }
            append("}")
        }
        try {
            rpc(DELETE_URL, body)
        } catch (exception: DropboxApiException) {
            // Already gone is the outcome that was asked for, and a file that changed since is one the next run
            // will see and bring back rather than destroy.
            if (!exception.errorSummary.startsWith("path_lookup/not_found") &&
                !exception.errorSummary.startsWith("path_write/conflict")
            ) {
                throw exception
            }
        }
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
            size = entry.size
        )
    }

    private fun remotePath(kind: LibraryFileKind, name: String) = "/${kind.id}/$name"

    // Requests

    /** A Dropbox "RPC" call: JSON in, JSON out, everything above the transport reported in the body. */
    private suspend fun rpc(url: String, body: String?): String {
        val token = accessToken()
        val response = transport {
            httpClient.post(url) {
                header("Authorization", "Bearer $token")
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
     * Anything the transport throws - no route to the host, a dropped connection, a timeout - is one thing to the
     * user: the service could not be reached, and trying again later is worth doing. Only the call itself is
     * wrapped, so that a body Campfire cannot make sense of stays the programming error it is.
     */
    private suspend fun <T> transport(block: suspend () -> T): T = try {
        block()
    } catch (exception: SyncAuthorizationException) {
        throw exception
    } catch (exception: SyncNetworkException) {
        throw exception
    } catch (exception: DropboxApiException) {
        throw exception
    } catch (exception: Exception) {
        throw SyncNetworkException(exception.message ?: "Dropbox could not be reached.", exception)
    }

    private suspend fun HttpResponse.ensureSuccessful() {
        if (status.isSuccess()) return
        val summary = errorSummary()
        throw when {
            status == HttpStatusCode.Unauthorized -> SyncAuthorizationException("Dropbox refused the token: $summary")
            // Dropbox says 429 for rate limiting and 5xx for its own trouble; both are worth trying again later.
            status == HttpStatusCode.TooManyRequests || status.value >= 500 -> SyncNetworkException("Dropbox is busy: ${status.value}")
            else -> DropboxApiException(status.value, summary)
        }
    }

    private suspend fun HttpResponse.errorSummary() = try {
        json.decodeFromString<DropboxErrorResponse>(bodyAsText()).errorSummary
    } catch (exception: Exception) {
        ""
    }

    /**
     * The stored access token, renewed first if it is about to expire. The whole check and renewal happen inside one
     * [SyncCredentialsStore.update], so that the several requests of a sync run cannot each start a refresh of their
     * own and have the last one to finish overwrite the tokens the others are using.
     */
    private suspend fun accessToken(): String = credentialsStore.update { credentials ->
        if (credentials == null || credentials.providerId != id.id) {
            throw SyncAuthorizationException("Dropbox is not connected.")
        }
        if (credentials.accessToken.isNotEmpty() && Clock.System.now().toEpochMilliseconds() < credentials.expiresAt - EXPIRY_MARGIN_MILLIS) {
            return@update credentials to credentials.accessToken
        }
        val token = exchange("grant_type=refresh_token&refresh_token=${credentials.refreshToken.urlEncode()}&client_id=${appKey.urlEncode()}")
        credentials.copy(
            accessToken = token.accessToken,
            expiresAt = expiryOf(token.expiresInSeconds),
            // Dropbox may hand out a new refresh token, and keeping the old one would end the connection silently.
            refreshToken = token.refreshToken ?: credentials.refreshToken
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
            throw SyncAuthorizationException("Dropbox refused the authorization: ${response.status.value} ${response.errorSummary()}")
        }
        return json.decodeFromString(response.bodyAsText())
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
        const val DELETE_URL = "https://api.dropboxapi.com/2/files/delete_v2"
        const val DOWNLOAD_URL = "https://content.dropboxapi.com/2/files/download"
        const val UPLOAD_URL = "https://content.dropboxapi.com/2/files/upload"

        /** Tokens are renewed slightly early, so that one does not expire between the check and the request. */
        const val EXPIRY_MARGIN_MILLIS = 60_000L

        val json = Json { ignoreUnknownKeys = true }
    }
}

/** A call Dropbox answered, with something other than success. */
internal class DropboxApiException(val statusCode: Int, val errorSummary: String) :
    Exception("Dropbox answered $statusCode: $errorSummary")
