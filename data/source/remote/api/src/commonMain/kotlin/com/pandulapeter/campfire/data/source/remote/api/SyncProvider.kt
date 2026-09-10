package com.pandulapeter.campfire.data.source.remote.api

import com.pandulapeter.campfire.data.model.domain.LibraryFileKind
import com.pandulapeter.campfire.data.model.domain.SyncAccount
import com.pandulapeter.campfire.data.model.domain.SyncProviderId
import com.pandulapeter.campfire.data.source.remote.api.model.RemoteAuthorizationRequest
import com.pandulapeter.campfire.data.source.remote.api.model.RemoteAuthorizationResponse
import com.pandulapeter.campfire.data.source.remote.api.model.RemoteListing
import com.pandulapeter.campfire.data.source.remote.api.model.RemoteWriteResult

/**
 * One cloud service, seen as a single flat folder of files addressed by (kind, name) - exactly the shape the library
 * itself has. Everything a provider does differently lives behind this: Dropbox works in paths, Drive in file ids,
 * WebDAV in URLs, and the sync engine above never learns which.
 *
 * The rules that keep a second provider cheap, all of them load bearing:
 * - Revisions and cursors are opaque strings. The engine stores them and hands them back, and never parses one.
 * - Names are unique. A service that allows two files with the same name in a folder (Drive does) has to resolve
 *   that inside its own [list], before the engine ever sees it.
 * - [delete] may be a move to a trash rather than a destruction, and the engine does not care which.
 * - Nothing here throws for an expired token: refreshing is the provider's own business, and only an authorization
 *   that cannot be repaired surfaces, as [SyncAuthorizationException].
 */
interface SyncProvider {

    val id: SyncProviderId

    /** True once there are credentials to work with, whether or not they still work. */
    suspend fun isConnected(): Boolean

    // Authorization

    /** Builds the URL to open in the browser. [redirectUri] is null on platforms that use the copy-a-code flow. */
    fun buildAuthorizationRequest(redirectUri: String?): RemoteAuthorizationRequest

    /**
     * Exchanges the code for tokens and stores them, then returns who the user turned out to be.
     *
     * @param verifier The PKCE verifier from the matching [RemoteAuthorizationRequest], which may have been read
     *   back from storage after a page reload.
     */
    suspend fun completeAuthorization(
        response: RemoteAuthorizationResponse,
        verifier: String,
        redirectUri: String?
    ): SyncAccount

    /** Forgets the stored credentials, and tells the service to drop them too where that is possible. */
    suspend fun disconnect()

    /** Who is connected, or null if the credentials are gone or no longer accepted. */
    suspend fun loadAccount(): SyncAccount?

    // Files

    /**
     * Everything in the remote library folder.
     *
     * Every run lists the whole folder rather than asking what changed. For a library of songs that is one request,
     * and it is right even when a previous run was interrupted half way - which a delta would not be. A provider
     * that wants to offer live updates has somewhere to put a cursor; nothing needs one yet.
     */
    suspend fun list(): RemoteListing

    suspend fun download(kind: LibraryFileKind, name: String): ByteArray

    /**
     * The hash of [bytes] in the same format as [RemoteFile.contentHash], or null where the provider offers none.
     *
     * Every service hashes differently - Dropbox over 4 MB blocks, Drive with MD5 - so the comparison has to happen
     * inside the provider. It only ever saves a transfer: two sides that hash the same are already in step, and a
     * provider that answers null simply transfers the file.
     */
    fun contentHashOf(bytes: ByteArray): String?

    /**
     * Creates or replaces a remote file. [expectedRevision] is the revision the caller last saw: the write must not
     * go through if the remote file has moved on since, and must report [RemoteWriteResult.Conflict] instead. Null
     * means the caller believes there is no such file yet, and a file that does exist is likewise a conflict.
     */
    suspend fun upload(
        kind: LibraryFileKind,
        name: String,
        bytes: ByteArray,
        expectedRevision: String?
    ): RemoteWriteResult

    /** Does nothing if the file is already gone, which is the outcome the caller wanted anyway. */
    suspend fun delete(kind: LibraryFileKind, name: String, expectedRevision: String?)
}

/** The credentials are gone, were refused or were revoked: only connecting again can fix it. */
class SyncAuthorizationException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** The service could not be reached. Trying again later is a reasonable thing to do. */
class SyncNetworkException(message: String, cause: Throwable? = null) : Exception(message, cause)
