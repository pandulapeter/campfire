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

import com.pandulapeter.campfire.data.model.library.LibraryCollections
import com.pandulapeter.campfire.data.sync.implementation.run.SyncEngine
import com.pandulapeter.campfire.data.model.library.SyncCollection
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
 * the engine may assume about them, and the content hash is the local one so that identical bytes compare equal. A
 * write expecting the revision of a file deleted since writes it again, as Dropbox's does.
 *
 * [onDownload] and [onUpload] run before a transfer answers, which is where a test makes the service fail part way
 * through a run or changes the local library under an operation that is already under way; [onList] runs before a
 * listing answers, which is where a test makes the whole run fail. [sizes] lets a listing, and the download limit,
 * see a large file without the test allocating it. [account] is who the service says is connected, which is what a
 * repository test restores the connection from. [ignoresCase] makes it a service like Dropbox, which takes two names
 * that differ only by case or Unicode form for one file. [resolvesListedNames] makes it a service with exact names
 * whose provider resolves a request through the spellings its last listing (and its own uploads since) named, as
 * [SyncProvider] asks of a provider for such a service. [listCount] is how many passes a run made, [downloadCounts]
 * how many times each file was fetched and [deleteCalls] what each deletion request asked for; the names in
 * [refusedDeletions] are answered as refused. A deletion whose expected revision is no longer the file's leaves the
 * file and reports nothing, as Dropbox does, and [onDelete] runs before deletions answer, which is where a test edits a
 * file "elsewhere"; [onDeleted] is told of every file a deletion removed, and [deletionPause] stops a batch part way. [connected] says whether credentials are stored, and [onDisconnect] is the revocation a disconnect
 * answers once it has cleared them, which the caller runs in the background; [hasForgottenCredentials] says whether they were dropped without one, which does not run it, and
 * [onForgetStoredCredentials] runs before they are, which is where a test makes that fail. [onCompleteAuthorization] is
 * what finishing an authorization does, which is where a test stores credentials and then gives up or fails. [id] is
 * Dropbox's unless a test stands a second provider beside it.
 * [listingFault] makes the next listing partial or stale, as a service's may be.
 * Every write and deletion goes into a feed of changes, read with [awaitChanges] and [changesSince] from the position a
 * listing's cursor names, and [writeElsewhere] and [deleteElsewhere] are another device's changes reaching the folder.
 */
internal class FakeSyncProvider(
    files: Map<SyncKey, ByteArray> = emptyMap(),
    private val sizes: Map<SyncKey, Long> = emptyMap(),
    var onDownload: suspend (SyncKey) -> Unit = {},
    var onUpload: suspend (SyncKey) -> Unit = {},
    private val account: SyncAccount? = null,
    private val ignoresCase: Boolean = false,
    private val resolvesListedNames: Boolean = false,
    override val id: SyncProviderId = SyncProviderId.DROPBOX,
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

    var onDelete: suspend () -> Unit = {}

    var onDeleted: (SyncKey) -> Unit = {}

    /**
     * Where the next batch deletion stops part way, as Dropbox's is not atomic (measured: a few files a second, every
     * listing meanwhile a consistent snapshot of any fraction): after as many of its files as [DeletionPause.after]
     * answers for the batch's size, [DeletionPause.during] runs, another device's run, say, before the rest go. Taken by
     * the batch it pauses.
     */
    var deletionPause: DeletionPause? = null

    class DeletionPause(val after: (size: Int) -> Int, val during: suspend () -> Unit)

    val files = files.mapValues { (_, bytes) -> bytes to "r1" }.toMutableMap()
    private var nextRevision = 2

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
        val fault = listingFault.also { listingFault = null }
        val folder = when (fault) {
            is ListingFault.Stale -> maxOf(writes - fault.writesAgo, fault.notBefore).let { at ->
                if (at >= writes) snapshot() else history.firstOrNull { it.writes == at } ?: history.firstOrNull() ?: snapshot()
            }
            else -> snapshot()
        }
        val isLeftOut = (fault as? ListingFault.Partial)?.isLeftOut ?: { false }
        return listing(
            folder.copy(
                files = folder.files.filterKeys { !isLeftOut(it.path) },
                sharedFiles = folder.sharedFiles.filterKeys { !isLeftOut("$SHARED_FOLDER/$it") },
            ),
        )
    }

    private fun listing(folder: Snapshot) = RemoteListing(
        cursor = folder.feedSize.toString(),
        documentRevisions = folder.documentRevisions,
        sharedFiles = folder.sharedFiles.map { (shareId, file) ->
            RemoteSharedFile(shareId = shareId, revision = file.second, contentHash = contentHashOf(file.first), size = file.first.size.toLong())
        },
        // The provider's own map of names comes from the folder as it is even where the listing does not: a fault here is
        // what the engine is told, while resolving a name is the provider's business, which a provider must get right.
        files = folder.files.also { listCount++ }.also { rememberListedSpellings(files) }.map { (key, file) ->
            remoteFile(key, file.first, file.second)
        },
    )

    /**
     * What the next [list] answers in place of the folder as it is, taken by that listing: [ListingFault.Partial] leaves
     * out the files and shared copies whose paths it picks while claiming to be whole, as a paging bug would, and
     * [ListingFault.Stale] answers the folder as it was some writes ago, as an eventually consistent service may, but
     * never as it was before [writes] stood at [ListingFault.Stale.notBefore]: a session's reads are never older than
     * what it read or wrote before, which the engine needs of a provider.
     */
    var listingFault: ListingFault? = null

    sealed interface ListingFault {
        class Partial(val isLeftOut: (path: String) -> Boolean) : ListingFault
        class Stale(val writesAgo: Int, val notBefore: Int) : ListingFault
    }

    /** The folder as a listing would see it. */
    private data class Snapshot(
        val files: Map<SyncKey, Pair<ByteArray, String>>,
        val documentRevisions: Map<String, String>,
        val sharedFiles: Map<String, Pair<ByteArray, String>>,
        val feedSize: Int,
        val writes: Int,
    )

    private fun snapshot() = Snapshot(
        files = files.toMap(),
        documentRevisions = documents.mapValues { it.value.second },
        sharedFiles = sharedFiles.toMap(),
        feedSize = feed.value.size,
        writes = writes,
    )

    /** The folder before each of the last writes, newest last, for [ListingFault.Stale]. */
    private val history = ArrayDeque<Snapshot>()

    /** How many writes and deletions the folder has taken. */
    var writes = 0
        private set

    /** Called before every write and deletion, so that a stale listing can answer what was there before it. */
    private fun recordHistory() {
        history.addLast(snapshot())
        if (history.size > HISTORY_SIZE) history.removeFirst()
        writes++
    }

    override suspend fun download(kind: SyncCollection, name: String, maxSize: Long): RemoteDocument {
        val key = SyncKey(kind = kind, name = name)
        downloadCounts[key] = (downloadCounts[key] ?: 0) + 1
        onDownload(key)
        val (bytes, revision) = files.getValue(stored(key))
        val size = sizes[key] ?: bytes.size.toLong()
        if (size > maxSize) error("\"${key.path}\" is $size bytes, more than the $maxSize asked for.")
        return RemoteDocument(bytes, revision)
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
        kind: SyncCollection,
        name: String,
        bytes: ByteArray,
        expectedRevision: String?,
    ): RemoteWriteResult {
        val key = SyncKey(kind = kind, name = name)
        onUpload(key)
        val target = stored(key)
        if (isConflict(files[target]?.second, expectedRevision)) return RemoteWriteResult.Conflict
        recordHistory()
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
        val pause = deletionPause.also { deletionPause = null }?.takeIf { deleted.size > 1 }
        val pausesAfter = pause?.after?.invoke(deleted.size)
        deleted.forEachIndexed { position, deletion ->
            if (position == pausesAfter) pause.during()
            val target = stored(SyncKey(kind = deletion.kind, name = deletion.name))
            if (deletion.expectedRevision == null || files[target]?.second == deletion.expectedRevision) {
                if (target in files) recordHistory()
                files.remove(target)?.let { (bytes, _) ->
                    deletedFiles[target] = bytes
                    onDeleted(target)
                }
                listedSpellings -= target.foldedForResolving()
                feed.value += target.path
            }
        }
        return refused.associateWith { "refused" }
    }

    /**
     * The last content of every file [delete] took out of the folder, which [restoreDeleted] puts back for as long as
     * the fake lives, as a service's history of deleted files does for a month or so; a test may plant its own.
     * [restoreRequests] is every name asked for.
     */
    val deletedFiles = mutableMapOf<SyncKey, ByteArray>()

    val restoreRequests = mutableListOf<SyncKey>()

    override suspend fun restoreDeleted(kind: SyncCollection, name: String): Boolean {
        val key = SyncKey(kind = kind, name = name)
        restoreRequests += key
        if (stored(key) in files) return true
        val bytes = deletedFiles[key] ?: return false
        recordHistory()
        files[key] = bytes to "r${nextRevision++}"
        feed.value += key.path
        return true
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
        if (isConflict(documents[name]?.second, expectedRevision)) return RemoteWriteResult.Conflict
        recordHistory()
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
    var onCreateSharedLink: suspend (String) -> SharedLinkResult = { shareId -> SharedLinkResult.Created(provider = id, parts = listOf("id-$shareId", "key")) }

    /** Runs before a shared copy's upload answers, which is where a test lets another device upload it first. */
    var onUploadSharedFile: suspend (String) -> Unit = {}

    /** Told of every shared copy an upload wrote. */
    var onSharedFileWritten: (String) -> Unit = {}

    override suspend fun uploadSharedFile(
        shareId: String,
        bytes: ByteArray,
        expectedRevision: String?,
    ): RemoteWriteResult {
        requireShareId(shareId)
        onUploadSharedFile(shareId)
        if (isConflict(sharedFiles[shareId]?.second, expectedRevision)) return RemoteWriteResult.Conflict
        recordHistory()
        val revision = "r${nextRevision++}"
        sharedFiles[shareId] = bytes to revision
        onSharedFileWritten(shareId)
        return RemoteWriteResult.Written(revision)
    }

    override suspend fun createSharedLink(shareId: String) = onCreateSharedLink(shareId.also(::requireShareId))

    /** Runs before a shared copy is deleted, which is where a test makes the service refuse it. */
    var onDeleteSharedFile: suspend (String) -> Unit = {}

    override suspend fun deleteSharedFile(shareId: String) {
        requireShareId(shareId)
        onDeleteSharedFile(shareId)
        if (shareId in sharedFiles) recordHistory()
        sharedFiles -= shareId
    }

    /** The folder as it is, whatever a listing answers: a fault strikes listings only. */
    override suspend fun hasSharedFile(shareId: String) = shareId.also(::requireShareId) in sharedFiles

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
        val keys = changed.mapNotNull { SyncKey.fromPath(it, LibraryCollections.all) }
        return RemoteChanges(
            files = keys.mapNotNull { key -> files[key]?.let { (bytes, revision) -> remoteFile(key, bytes, revision) } },
            deletedFiles = keys.filter { it !in files }.map { RemoteChanges.DeletedFile(kind = it.kind, name = it.name) },
            documents = changed.filterTo(mutableSetOf()) { SyncKey.fromPath(it, LibraryCollections.all) == null },
            cursor = feed.value.size.toString(),
        )
    }

    /** Another device writing [key]. */
    fun writeElsewhere(key: SyncKey, bytes: ByteArray) {
        recordHistory()
        files[key] = bytes to "r${nextRevision++}"
        feed.value += key.path
    }

    /** Another device deleting [key]. */
    fun deleteElsewhere(key: SyncKey) {
        recordHistory()
        files -= key
        feed.value += key.path
    }

    /** Another device writing the document [name]. */
    fun writeDocumentElsewhere(name: String, bytes: ByteArray) {
        recordHistory()
        documents[name] = bytes to "r${nextRevision++}"
        feed.value += name
    }

    /**
     * Whether a write expecting [expectedRevision] of a file now at [current] is refused, as Dropbox refuses it
     * (measured): a new file where one exists, or a file written since. A file deleted since is written again whatever
     * revision the write expected, its folder too, so an upload never learns that a deletion came first.
     */
    private fun isConflict(current: String?, expectedRevision: String?) = current != null && current != expectedRevision

    private companion object {
        /** How many writes back a stale listing may reach. */
        const val HISTORY_SIZE = 8

        const val SHARED_FOLDER = "shared"
    }

    private fun remoteFile(key: SyncKey, bytes: ByteArray, revision: String) = RemoteFile(
        kind = key.kind,
        name = key.name,
        revision = revision,
        contentHash = contentHashOf(bytes),
        size = sizes[key] ?: bytes.size.toLong(),
    )
}
