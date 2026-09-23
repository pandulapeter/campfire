/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.remote.api

import com.pandulapeter.campfire.data.model.domain.LibraryFileKind
import com.pandulapeter.campfire.data.model.domain.SyncAccount
import com.pandulapeter.campfire.data.model.domain.SyncProviderId
import com.pandulapeter.campfire.data.source.remote.api.model.RemoteAuthorizationRequest
import com.pandulapeter.campfire.data.source.remote.api.model.RemoteAuthorizationResponse
import com.pandulapeter.campfire.data.source.remote.api.model.RemoteDeletion
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
 * - An account that is out of space says so with [SyncRemoteStorageFullException], from [upload] only. It ends the
 *   run; any other refusal is one file's problem.
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
        redirectUri: String?,
    ): SyncAccount

    /** Forgets the stored credentials, and tells the service to drop them too where that is possible. */
    suspend fun disconnect()

    /**
     * Forgets the stored credentials and tells the service nothing. [disconnect] is the user's disconnect, which
     * also revokes the token; this is an installation dropping what a previous one left in a store that outlived
     * it, where there is nobody to revoke on behalf of and no permission to make a request.
     */
    suspend fun forgetStoredCredentials()

    /** Who is connected, or null if the credentials are gone or no longer accepted. */
    suspend fun loadAccount(): SyncAccount?

    /**
     * Who is connected as far as this device remembers, answered from what is stored and without a request - so that
     * a start up on a bad network can show the account at once and leave [loadAccount] to catch up behind it. Null
     * when nothing is connected, or when nothing was ever stored that the account could go by.
     */
    suspend fun storedAccount(): SyncAccount?

    // Files

    /**
     * Everything in the remote library folder.
     *
     * Every run lists the whole folder rather than asking what changed. For a library of songs that is one request,
     * and it is right even when a previous run was interrupted half way - which a delta would not be. A provider
     * that wants to offer live updates has somewhere to put a cursor; nothing needs one yet. Whatever else the user
     * keeps in the folder may be listed too. The engine leaves out what [LibraryFileKind.matches] does not recognise
     * and refuses to download what is too large to be a song, so a provider does neither.
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
     *
     * Throws [SyncNetworkException] whenever it cannot tell whether the write went through. Any other exception means
     * it did not: the engine takes back work it did in preparation for the write on the strength of that.
     */
    suspend fun upload(
        kind: LibraryFileKind,
        name: String,
        bytes: ByteArray,
        expectedRevision: String?,
    ): RemoteWriteResult

    /**
     * Deletes every file in [deletions], as one operation where the service has one, and answers the ones it could not
     * delete, each with the reason. A file that is already gone counts as deleted, since that is the outcome the caller
     * wanted, and so does one whose revision is no longer the expected one: it is left where it is, and the next run
     * sees it as a file that changed rather than one to destroy.
     *
     * All of a run's deletions come in one call on purpose. Another device that lists the folder while they are
     * under way sees some of the files gone and some still there, and the share it sees gone is what decides whether
     * it asks before following - so the shorter that stretch is, the less of a large deletion can reach a device
     * without its question. Use the service's bulk call where it has one, even one that is not atomic; without one,
     * delete one file after the other as fast as the service allows.
     *
     * Throws what the other calls throw for a run that cannot go on; one file's refusal is an entry in the answer.
     */
    suspend fun delete(deletions: List<RemoteDeletion>): Map<RemoteDeletion, String>
}

/** The credentials are gone, were refused or were revoked: only connecting again can fix it. */
class SyncAuthorizationException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** The service could not be reached. Trying again later is a reasonable thing to do. */
class SyncNetworkException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * The service has no room left for what is being uploaded. Told apart from the refusal of one file because every
 * upload after it would be answered the same way, each only after sending its file.
 */
class SyncRemoteStorageFullException(message: String, cause: Throwable? = null) : Exception(message, cause)
