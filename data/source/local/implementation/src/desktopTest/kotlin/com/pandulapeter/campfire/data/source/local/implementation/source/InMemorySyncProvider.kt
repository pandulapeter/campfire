/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.source

import com.pandulapeter.campfire.data.model.library.LibraryFileKind
import com.pandulapeter.campfire.data.model.sync.SyncAccount
import com.pandulapeter.campfire.data.model.sync.SyncProviderId
import com.pandulapeter.campfire.data.source.remote.api.hashing.localContentHash
import com.pandulapeter.campfire.data.source.remote.api.sync.SyncProvider
import com.pandulapeter.campfire.data.source.remote.api.sync.model.RemoteAuthorizationRequest
import com.pandulapeter.campfire.data.source.remote.api.sync.model.RemoteAuthorizationResponse
import com.pandulapeter.campfire.data.source.remote.api.sync.model.RemoteChanges
import com.pandulapeter.campfire.data.source.remote.api.sync.model.RemoteDeletion
import com.pandulapeter.campfire.data.source.remote.api.sync.model.RemoteDocument
import com.pandulapeter.campfire.data.source.remote.api.sync.model.RemoteFile
import com.pandulapeter.campfire.data.source.remote.api.sync.model.RemoteListing
import com.pandulapeter.campfire.data.source.remote.api.sync.model.RemoteSharedFile
import com.pandulapeter.campfire.data.source.remote.api.sync.model.RemoteWriteResult
import com.pandulapeter.campfire.data.source.remote.api.sync.model.SharedLinkResult
import kotlinx.coroutines.awaitCancellation
import java.util.IdentityHashMap

/**
 * A cloud folder held in memory, connected from the start, in place of Dropbox for [SharedCopyApp]: names are exact,
 * revisions a counter, and the content hash the local one, so identical bytes compare equal. Every request holds the
 * folder's lock, since a run transfers several files at once from the app's threads. [sharedUploads] counts
 * every shared copy written, by share id, which is what tells a run that rebuilt a copy to the same bytes (none) from
 * one whose builder disagreed with the share's (one).
 */
internal class InMemorySyncProvider : SyncProvider {

    override val id = SyncProviderId.DROPBOX

    private val files = mutableMapOf<Pair<LibraryFileKind, String>, Pair<ByteArray, String>>()
    private val documents = mutableMapOf<String, Pair<ByteArray, String>>()
    private var nextRevision = 1

    /** The hash of every file held, by the very array, since a listing names each and a run lists more than once. */
    private val hashes = IdentityHashMap<ByteArray, String>()

    /** The shared copies by share id, with their revisions. */
    val sharedFiles = mutableMapOf<String, Pair<ByteArray, String>>()

    val sharedUploads = mutableMapOf<String, Int>()

    override suspend fun isConnected() = true

    override fun buildAuthorizationRequest(redirectUri: String?) = throw UnsupportedOperationException()

    override suspend fun completeAuthorization(response: RemoteAuthorizationResponse, verifier: String, redirectUri: String?) =
        throw UnsupportedOperationException()

    override suspend fun disconnect() = null

    override suspend fun forgetStoredCredentials() = Unit

    override suspend fun loadAccount() = ACCOUNT

    override suspend fun storedAccount() = ACCOUNT

    override suspend fun list() = synchronized(this) { listing() }

    private fun listing() = RemoteListing(
        cursor = "0",
        documentRevisions = documents.mapValues { it.value.second },
        sharedFiles = sharedFiles.map { (shareId, file) ->
            RemoteSharedFile(shareId = shareId, revision = file.second, contentHash = hashOf(file.first), size = file.first.size.toLong())
        },
        files = files.map { (key, file) ->
            RemoteFile(kind = key.first, name = key.second, revision = file.second, contentHash = hashOf(file.first), size = file.first.size.toLong())
        },
    )

    private fun hashOf(bytes: ByteArray) = hashes.getOrPut(bytes) { contentHashOf(bytes) }

    override suspend fun awaitChanges(cursor: String): Boolean = awaitCancellation()

    override suspend fun changesSince(cursor: String) =
        RemoteChanges(files = emptyList(), deletedFiles = emptyList(), documents = emptySet(), cursor = cursor)

    override suspend fun download(kind: LibraryFileKind, name: String, maxSize: Long): RemoteDocument {
        val (bytes, revision) = synchronized(this) { files.getValue(kind to name) }
        check(bytes.size <= maxSize) { "$name is ${bytes.size} bytes, more than the $maxSize asked for." }
        return RemoteDocument(bytes, revision)
    }

    override fun contentHashOf(bytes: ByteArray) = localContentHash(bytes)

    override suspend fun upload(kind: LibraryFileKind, name: String, bytes: ByteArray, expectedRevision: String?) =
        write(files, kind to name, bytes, expectedRevision)

    override suspend fun delete(deletions: List<RemoteDeletion>): Map<RemoteDeletion, String> = synchronized(this) {
        deletions.forEach { deletion ->
            val key = deletion.kind to deletion.name
            if (deletion.expectedRevision == null || files[key]?.second == deletion.expectedRevision) files -= key
        }
        emptyMap()
    }

    override suspend fun restoreDeleted(kind: LibraryFileKind, name: String) = synchronized(this) { (kind to name) in files }

    override suspend fun downloadDocument(name: String) =
        synchronized(this) { documents[name] }?.let { (bytes, revision) -> RemoteDocument(bytes, revision) }

    override suspend fun uploadDocument(name: String, bytes: ByteArray, expectedRevision: String?) = write(documents, name, bytes, expectedRevision)

    override suspend fun uploadSharedFile(shareId: String, bytes: ByteArray, expectedRevision: String?) = synchronized(this) {
        write(sharedFiles, shareId, bytes, expectedRevision).also { result ->
            if (result is RemoteWriteResult.Written) sharedUploads[shareId] = (sharedUploads[shareId] ?: 0) + 1
        }
    }

    override suspend fun createSharedLink(shareId: String) = SharedLinkResult.Created(provider = id, parts = listOf("id-$shareId", "key"))

    override suspend fun deleteSharedFile(shareId: String) {
        synchronized(this) { sharedFiles -= shareId }
    }

    /**
     * Forgets the folder. A closed app's jobs may outlive it for a while, waiting on a delay, and keep their provider
     * reachable meanwhile: without its files, a seed with large attachments no longer holds them for that long.
     */
    fun clear() = synchronized(this) {
        files.clear()
        documents.clear()
        sharedFiles.clear()
        hashes.clear()
    }

    private fun <K> write(
        folder: MutableMap<K, Pair<ByteArray, String>>,
        key: K,
        bytes: ByteArray,
        expectedRevision: String?,
    ): RemoteWriteResult = synchronized(this) {
        if (folder[key]?.second != expectedRevision) {
            RemoteWriteResult.Conflict
        } else {
            val revision = "r${nextRevision++}"
            folder[key] = bytes to revision
            RemoteWriteResult.Written(revision)
        }
    }

    private companion object {
        val ACCOUNT = SyncAccount(providerId = SyncProviderId.DROPBOX, id = "account", displayName = "Someone", email = null)
    }
}
