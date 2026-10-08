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
import com.pandulapeter.campfire.data.model.domain.SyncProviderId
import com.pandulapeter.campfire.data.source.remote.api.model.RemoteDeletion
import com.pandulapeter.campfire.data.source.remote.api.model.RemoteDocument
import com.pandulapeter.campfire.data.source.remote.api.model.RemoteListing
import com.pandulapeter.campfire.data.source.remote.api.model.RemoteWriteResult

/**
 * The files half of a [SyncProvider]: one cloud folder, flat, addressed by (kind, name) - exactly the shape the library
 * itself has, and everything a run reads and writes.
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
interface SyncFolder {

    val id: SyncProviderId

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

    /**
     * The file as it is now, with the revision of what was fetched rather than the one [list] named: another device may
     * have written the file again in between, and the engine records the revision of the content it actually holds.
     */
    suspend fun download(kind: LibraryFileKind, name: String): RemoteDocument

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

    // Documents

    /**
     * A document of the folder's own, outside the two library folders and so never in [list], or null where there is
     * none. Unlike a library file it is asked for by name alone and comes back with its revision, since it is read
     * once per run rather than listed and compared.
     */
    suspend fun downloadDocument(name: String): RemoteDocument?

    /**
     * Writes a document of the folder's own, with the same rules as [upload]: a document that has moved on from
     * [expectedRevision], or that exists although [expectedRevision] is null, is a [RemoteWriteResult.Conflict].
     */
    suspend fun uploadDocument(
        name: String,
        bytes: ByteArray,
        expectedRevision: String?,
    ): RemoteWriteResult
}
