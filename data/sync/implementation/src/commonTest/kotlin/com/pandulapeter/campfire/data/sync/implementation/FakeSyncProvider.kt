/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.sync.implementation

import com.pandulapeter.campfire.data.sync.implementation.run.SyncEngine
import com.pandulapeter.campfire.data.model.library.LibraryFileKind
import com.pandulapeter.campfire.data.model.library.LibraryFiles
import com.pandulapeter.campfire.data.model.sync.SyncAccount
import com.pandulapeter.campfire.data.model.sync.SyncProviderId
import com.pandulapeter.campfire.data.source.remote.api.sync.SyncDocumentTooLargeException
import com.pandulapeter.campfire.data.source.remote.api.sync.SyncProvider
import com.pandulapeter.campfire.data.source.remote.api.hashing.localContentHash
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

/**
 * A remote folder held in memory, for running [SyncEngine] against. Revisions are a counter per write, which is all
 * the engine may assume about them, and the content hash is the local one so that identical bytes compare equal.
 *
 * [onDownload] and [onUpload] run before a transfer answers, which is where a test makes the service fail part way
 * through a run or changes the local library under an operation that is already under way; [onList] runs before a
 * listing answers, which is where a test makes the whole run fail. [sizes] lets a listing
 * report a large file without the test allocating it. [account] is who the service says is connected, which is what a
 * repository test restores the connection from. [ignoresCase] makes it a service like Dropbox, which takes two names
 * that differ only by case or Unicode form for one file. [resolvesListedNames] makes it a service with exact names
 * whose provider resolves a request through the spellings its last listing (and its own uploads since) named, as
 * [SyncProvider] asks of a provider for such a service. [listCount] is how many passes a run made, [downloadCounts]
 * how many times each file was fetched and [deleteCalls] what each deletion request asked for; the names in
 * [refusedDeletions] are answered as refused. A deletion whose expected revision is no longer the file's leaves the
 * file and reports nothing, as Dropbox does, and [onDelete] runs before deletions answer, which is where a test edits a
 * file "elsewhere". [connected] says whether credentials are stored, and [onDisconnect] is the revocation a disconnect
 * answers once it has cleared them, which the caller runs in the background; [hasForgottenCredentials] says whether they were dropped without one, which does not run it, and
 * [onForgetStoredCredentials] runs before they are, which is where a test makes that fail. [onCompleteAuthorization] is
 * what finishing an authorization does, which is where a test stores credentials and then gives up or fails.
 * Every write and deletion goes into a feed of changes, read with [awaitChanges] and [changesSince] from the position a
 * listing's cursor names, and [writeElsewhere] and [deleteElsewhere] are another device's changes reaching the folder.
 */
internal class FakeSyncProvider(
    files: Map<SyncKey, ByteArray> = emptyMap(),
    private val sizes: Map<SyncKey, Long> = emptyMap(),
    var onDownload: suspend (SyncKey) -> Unit = {},
    var onUpload: (SyncKey) -> Unit = {},
    private val account: SyncAccount? = null,
    private val ignoresCase: Boolean = false,
    private val resolvesListedNames: Boolean = false,
) : SyncProvider {

    /** The provider's own map from a folded name to the spelling the folder holds, for [resolvesListedNames]. */
    private val listedSpellings = mutableMapOf<SyncKey, SyncKey>()

    var listCount = 0

    var onList: suspend () -> Unit = {}

    var connected = true

    var onDisconnect: suspend () -> Unit = {}

    var hasForgottenCredentials = false

    var onForgetStoredCredentials: suspend () -> Unit = {}

    val downloadCounts = mutableMapOf<SyncKey, Int>()

    val deleteCalls = mutableListOf<List<RemoteDeletion>>()

    var refusedDeletions = emptySet<String>()

    var onDelete: () -> Unit = {}

    val files = files.mapValues { (_, bytes) -> bytes to "r1" }.toMutableMap()
    private var nextRevision = 2

    override val id = SyncProviderId.DROPBOX

    /** Runs before every question about the stored credentials, which is where a test makes reading them fail. */
    var onIsConnected: suspend () -> Unit = {}

    override suspend fun isConnected(): Boolean {
        onIsConnected()
        return connected
    }

    override fun buildAuthorizationRequest(redirectUri: String?) = RemoteAuthorizationRequest(
        authorizationUrl = "https://example.com/authorize",
        redirectUri = redirectUri,
        state = "state",
        verifier = "verifier",
    )

    /** What finishing an authorization does; by default being asked is a mistake of the test. */
    var onCompleteAuthorization: suspend () -> SyncAccount = { throw UnsupportedOperationException() }

    override suspend fun completeAuthorization(
        response: RemoteAuthorizationResponse,
        verifier: String,
        redirectUri: String?,
    ): SyncAccount = onCompleteAuthorization()

    override suspend fun disconnect(): suspend () -> Unit {
        connected = false
        return { onDisconnect() }
    }

    override suspend fun forgetStoredCredentials() {
        onForgetStoredCredentials()
        connected = false
        hasForgottenCredentials = true
    }

    override suspend fun loadAccount() = account

    override suspend fun storedAccount() = null

    override suspend fun list(): RemoteListing {
        onList()
        return listing()
    }

    private fun listing() = RemoteListing(
        cursor = feed.value.size.toString(),
        documentRevisions = documents.mapValues { it.value.second },
        sharedFiles = sharedFiles.map { (shareId, file) ->
            RemoteSharedFile(shareId = shareId, revision = file.second, contentHash = contentHashOf(file.first), size = file.first.size.toLong())
        },
        files = files.also { listCount++ }.also(::rememberListedSpellings).map { (key, file) ->
            remoteFile(key, file.first, file.second)
        },
    )

    override suspend fun download(kind: LibraryFileKind, name: String): RemoteDocument {
        val key = SyncKey(kind = kind, name = name)
        downloadCounts[key] = (downloadCounts[key] ?: 0) + 1
        onDownload(key)
        return files.getValue(stored(key)).let { (bytes, revision) -> RemoteDocument(bytes, revision) }
    }

    override fun contentHashOf(bytes: ByteArray) = localContentHash(bytes)

    private fun rememberListedSpellings(listed: Map<SyncKey, *>) {
        listedSpellings.clear()
        listed.keys.forEach { listedSpellings[it.foldedForResolving()] = it }
    }

    /**
     * Where a request lands: on a service that ignores case and Unicode form, the stored spelling of the name, as
     * Dropbox keeps it; through [resolvesListedNames], the spelling the provider last saw for it.
     */
    private fun stored(key: SyncKey) = when {
        ignoresCase -> files.keys.firstOrNull { it.kind == key.kind && LibraryFiles.isSameLibraryName(it.name, key.name) } ?: key
        resolvesListedNames -> listedSpellings[key.foldedForResolving()] ?: key
        else -> key
    }

    private fun SyncKey.foldedForResolving() = copy(name = LibraryFiles.identityKey(name))

    override suspend fun upload(
        kind: LibraryFileKind,
        name: String,
        bytes: ByteArray,
        expectedRevision: String?,
    ): RemoteWriteResult {
        val key = SyncKey(kind = kind, name = name)
        onUpload(key)
        val target = stored(key)
        if (files[target]?.second != expectedRevision) return RemoteWriteResult.Conflict
        val revision = "r${nextRevision++}"
        files[target] = bytes to revision
        if (resolvesListedNames) listedSpellings[target.foldedForResolving()] = target
        feed.value += target.path
        return RemoteWriteResult.Written(revision)
    }

    override suspend fun delete(deletions: List<RemoteDeletion>): Map<RemoteDeletion, String> {
        deleteCalls += deletions
        onDelete()
        val (refused, deleted) = deletions.partition { it.name in refusedDeletions }
        deleted.forEach { deletion ->
            val target = stored(SyncKey(kind = deletion.kind, name = deletion.name))
            if (deletion.expectedRevision == null || files[target]?.second == deletion.expectedRevision) {
                files -= target
                listedSpellings -= target.foldedForResolving()
                feed.value += target.path
            }
        }
        return refused.associateWith { "refused" }
    }

    /** The folder's own documents, by name, with their revisions. */
    val documents = mutableMapOf<String, Pair<ByteArray, String>>()

    /** Runs before a document upload answers, which is where a test writes the document "elsewhere" under it. */
    var onUploadDocument: suspend (String) -> Unit = {}

    /** Runs once a document upload has written, before it answers, which is where a test loses the answer. */
    var onDocumentWritten: suspend (String) -> Unit = {}

    /**
     * Runs before [downloadDocument] answers, which is where a test makes it fail or holds it up. The document is taken
     * before it runs, so a held-up download answers what was there when it was asked, as a slow request does.
     */
    var onDownloadDocument: suspend () -> Unit = {}

    /** How many times [downloadDocument] was asked. */
    var documentDownloadCount = 0

    /** Documents too large to download, refused with their revision until a write replaces them. */
    val oversizedDocuments = mutableSetOf<String>()

    override suspend fun downloadDocument(name: String): RemoteDocument? {
        documentDownloadCount++
        val document = documents[name]?.let { (bytes, revision) -> RemoteDocument(bytes, revision) }
        onDownloadDocument()
        if (document != null && name in oversizedDocuments) throw SyncDocumentTooLargeException(revision = document.revision)
        return document
    }

    override suspend fun uploadDocument(
        name: String,
        bytes: ByteArray,
        expectedRevision: String?,
    ): RemoteWriteResult {
        onUploadDocument(name)
        if (documents[name]?.second != expectedRevision) return RemoteWriteResult.Conflict
        val revision = "r${nextRevision++}"
        documents[name] = bytes to revision
        oversizedDocuments -= name
        feed.value += name
        onDocumentWritten(name)
        return RemoteWriteResult.Written(revision)
    }

    /** The shared copies by share id, with their revisions. Never in the feed of changes, as the contract says. */
    val sharedFiles = mutableMapOf<String, Pair<ByteArray, String>>()

    /** What [createSharedLink] answers for a share id. */
    var onCreateSharedLink: suspend (String) -> SharedLinkResult = { shareId -> SharedLinkResult.Created(fileId = "id-$shareId", rlkey = "key") }

    /** Runs before a shared copy's upload answers, which is where a test lets another device upload it first. */
    var onUploadSharedFile: suspend (String) -> Unit = {}

    override suspend fun uploadSharedFile(
        shareId: String,
        bytes: ByteArray,
        expectedRevision: String?,
    ): RemoteWriteResult {
        requireShareId(shareId)
        onUploadSharedFile(shareId)
        if (sharedFiles[shareId]?.second != expectedRevision) return RemoteWriteResult.Conflict
        val revision = "r${nextRevision++}"
        sharedFiles[shareId] = bytes to revision
        return RemoteWriteResult.Written(revision)
    }

    override suspend fun createSharedLink(shareId: String) = onCreateSharedLink(shareId.also(::requireShareId))

    /** Runs before a shared copy is deleted, which is where a test makes the service refuse it. */
    var onDeleteSharedFile: suspend (String) -> Unit = {}

    override suspend fun deleteSharedFile(shareId: String) {
        requireShareId(shareId)
        onDeleteSharedFile(shareId)
        sharedFiles -= shareId
    }

    /** Refuses an id that is not a plain name before anything else, as the contract of the shared copies has it. */
    private fun requireShareId(shareId: String) = require(shareId.isNotEmpty() && shareId.all { it.isLetterOrDigit() || it == '-' || it == '_' }) {
        "Not a share id: $shareId"
    }

    /** Every file path and document name written or deleted, in order; a cursor is a position in it. */
    private val feed = MutableStateFlow(emptyList<String>())

    /** How many times [awaitChanges] was asked. */
    var awaitCount = 0

    /** Runs before [changesSince] answers, which is where a test makes it fail. */
    var onChangesSince: suspend () -> Unit = {}

    /** Answers the next waits in place of the feed, in order: where a test makes one fail, or end with nothing changed. */
    val awaitAnswers = ArrayDeque<suspend () -> Boolean>()

    override suspend fun awaitChanges(cursor: String): Boolean {
        awaitCount++
        awaitAnswers.removeFirstOrNull()?.let { return it() }
        feed.first { it.size > cursor.toInt() }
        return true
    }

    override suspend fun changesSince(cursor: String): RemoteChanges {
        onChangesSince()
        val changed = feed.value.drop(cursor.toInt()).toSet()
        val keys = changed.mapNotNull(SyncKey::fromPath)
        return RemoteChanges(
            files = keys.mapNotNull { key -> files[key]?.let { (bytes, revision) -> remoteFile(key, bytes, revision) } },
            deletedFiles = keys.filter { it !in files }.map { RemoteChanges.DeletedFile(kind = it.kind, name = it.name) },
            documents = changed.filterTo(mutableSetOf()) { SyncKey.fromPath(it) == null },
            cursor = feed.value.size.toString(),
        )
    }

    /** Another device writing [key]. */
    fun writeElsewhere(key: SyncKey, bytes: ByteArray) {
        files[key] = bytes to "r${nextRevision++}"
        feed.value += key.path
    }

    /** Another device deleting [key]. */
    fun deleteElsewhere(key: SyncKey) {
        files -= key
        feed.value += key.path
    }

    /** Another device writing the document [name]. */
    fun writeDocumentElsewhere(name: String, bytes: ByteArray) {
        documents[name] = bytes to "r${nextRevision++}"
        feed.value += name
    }

    private fun remoteFile(key: SyncKey, bytes: ByteArray, revision: String) = RemoteFile(
        kind = key.kind,
        name = key.name,
        revision = revision,
        contentHash = contentHashOf(bytes),
        size = sizes[key] ?: bytes.size.toLong(),
    )
}
