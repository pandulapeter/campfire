/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.repository.implementation.sync

import com.pandulapeter.campfire.data.model.domain.SyncProgress
import com.pandulapeter.campfire.data.model.domain.SyncSummary
import com.pandulapeter.campfire.data.source.local.api.LibraryFileLocalSource
import com.pandulapeter.campfire.data.source.remote.api.SyncAuthorizationException
import com.pandulapeter.campfire.data.source.remote.api.SyncNetworkException
import com.pandulapeter.campfire.data.source.remote.api.SyncProvider
import com.pandulapeter.campfire.data.source.remote.api.hashing.localContentHash
import com.pandulapeter.campfire.data.source.remote.api.model.RemoteWriteResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

/**
 * Gives a remote file the local spelling of its name where the two differ only by case.
 *
 * Dropbox paths are case-insensitive, so `Song.cho` here and `song.cho` there are one file to the service and two
 * keys to [SyncPlanner]: the new-file upload of the local one would be refused as a conflict on every pass, and the
 * file would never move. This is a property of one service surfacing in the engine on purpose - it keeps
 * [SyncProvider]'s contract down to a flat folder of names - and it costs a service with exact names nothing, since it
 * only ever renames a remote key that has no exact local match. Every later request uses the local spelling, which
 * the service resolves to the same file.
 */
internal fun foldRemoteNamesOntoLocal(local: List<LocalFileState>, remote: List<RemoteFileState>): List<RemoteFileState> {
    val localKeys = local.mapTo(mutableSetOf()) { it.key }
    val localKeysByFolded = local.associate { it.key.folded() to it.key }
    return remote.map { file ->
        if (file.key in localKeys) file else localKeysByFolded[file.key.folded()]?.let { file.copy(key = it) } ?: file
    }
}

private fun SyncKey.folded() = copy(name = name.lowercase())

/**
 * Carries out what [SyncPlanner] worked out.
 *
 * Everything here is written so that a run which is interrupted - the network drops, the app is killed, the user
 * stops it - leaves the library usable and the next run able to pick up: the index is only told about a file once
 * that file has actually moved, so anything half done simply looks unsynced next time rather than done. The index is
 * handed out as it grows rather than only when a pass completes, because a run that is stopped after four hundred
 * downloads has to keep those four hundred: forgotten, every one of them would come back as a file present on both
 * sides with nothing to say which is newer, and any the user edited in between would end up as a conflict copy.
 *
 * A failure on one file does not end the run. A song the storage cannot read must not keep the other four hundred
 * from travelling, so only the two failures that make every further call pointless - the credentials being refused
 * and the service being unreachable - stop it.
 */
internal class SyncEngine(
    private val libraryFileLocalSource: LibraryFileLocalSource,
) {

    suspend fun synchronize(
        provider: SyncProvider,
        document: SyncIndexDocument,
        accountId: String,
        onProgress: (SyncProgress) -> Unit,
        onIndexChanged: suspend (SyncIndexDocument) -> Unit,
    ): Result {
        // An index written for a different account describes a different remote folder, and acting on it would read
        // that folder's absent files as deletions of this one's songs.
        var index = document.takeIf { it.accountId == accountId }?.toIndex().orEmpty()
        var summary = SyncSummary()

        // Two passes at most. A file that a second device changed between this run's listing and its upload comes
        // back as a conflict; the second pass sees the revision it actually has now and resolves it properly. If it
        // happens again the run stops rather than chasing a device that is writing continuously, and the next sync
        // settles it.
        var pass = 0
        while (pass < MAXIMUM_PASSES) {
            // Listing both sides is the part of a run with nothing to show for it yet, so the progress reported
            // here has no total and the indicator spins rather than sitting at zero.
            onProgress(SyncProgress())
            val files = provider.list().files
            val local = readLocalStates()
            val remote = foldRemoteNamesOntoLocal(
                local = local,
                remote = files.map {
                    RemoteFileState(
                        key = SyncKey(kind = it.kind, name = it.name),
                        revision = it.revision,
                        contentHash = it.contentHash,
                    )
                },
            )
            val plan = SyncPlanner.plan(local = local, remote = remote, index = index)
            // Which is what most runs find, so nothing below this costs anything on an ordinary launch.
            if (plan.isEmpty()) break
            val outcome = apply(
                provider = provider,
                plan = plan,
                index = index,
                contentHashes = remote.associate { it.key to it.contentHash },
                onProgress = onProgress,
                accountId = accountId,
                lastSyncedAt = document.lastSyncedAt,
                onIndexChanged = onIndexChanged,
            )
            index = outcome.index
            summary = summary.plus(outcome.summary)
            if (!outcome.hasUnresolvedConflicts) break
            pass++
        }

        return Result(
            summary = summary,
            index = SyncIndexDocument.of(
                providerId = provider.id.id,
                accountId = accountId,
                lastSyncedAt = document.lastSyncedAt,
                index = index,
            ),
        )
    }

    /** Reading and hashing every file is the slow part of the preparation, so the files are read in parallel. */
    private suspend fun readLocalStates(): List<LocalFileState> = coroutineScope {
        // In batches for the same reason as the song scan in SongLocalSourceImpl: unbounded, a large library is
        // thousands of open handles and all of its bytes in memory at once.
        libraryFileLocalSource.loadLibraryFiles()
            .chunked(READ_BATCH_SIZE)
            .flatMap { batch ->
                batch.map { file ->
                    async {
                        val key = SyncKey(kind = file.kind, name = file.name)
                        libraryFileLocalSource.readLibraryFile(file.kind, file.name)
                            ?.let { LocalFileState(key = key, hash = localContentHash(it)) }
                    }
                }.awaitAll()
            }
            .filterNotNull()
    }

    /**
     * Every operation is at least one request of its own, and they used to be run one after another - which made a
     * first sync of a few hundred songs a few hundred round trips end to end, and the slowest thing the app does by
     * a wide margin. Files do not depend on each other, so they go at once, [CONCURRENT_TRANSFERS] at a time: enough
     * to hide the latency, few enough that the service answers with files rather than with rate limiting.
     *
     * The ordering that does matter is kept between the groups: incoming files first, then outgoing ones, then the
     * deletions. A download has to be on disk before anything that reads the library acts on it, and a deletion that
     * ran before a download would undo it.
     *
     * [onIndexChanged] is called under the same lock the results are merged under, so the snapshots arrive in the
     * order they were taken and the last one handed out is always the most complete.
     */
    private suspend fun apply(
        provider: SyncProvider,
        plan: List<SyncOperation>,
        index: Map<SyncKey, SyncIndexEntry>,
        contentHashes: Map<SyncKey, String?>,
        onProgress: (SyncProgress) -> Unit,
        accountId: String,
        lastSyncedAt: Long,
        onIndexChanged: suspend (SyncIndexDocument) -> Unit,
    ): PassOutcome = coroutineScope {
        val updated = index.toMutableMap()
        var summary = SyncSummary()
        var hasUnresolvedConflicts = false
        var completed = 0
        // Whichever request finishes first writes to all four of those, so the merging is done in one place.
        val results = Mutex()
        val permits = Semaphore(CONCURRENT_TRANSFERS)
        onProgress(SyncProgress(completed = 0, total = plan.size))

        plan.groupBy { it.order }.entries.sortedBy { it.key }.forEach { (_, group) ->
            group.map { operation ->
                async {
                    val outcome = permits.withPermit { runOperation(provider, operation, index, contentHashes) }
                    results.withLock {
                        updated += outcome.entries
                        updated -= outcome.removals
                        summary = summary.plus(outcome.summary)
                        hasUnresolvedConflicts = hasUnresolvedConflicts || outcome.hasUnresolvedConflict
                        completed++
                        onProgress(SyncProgress(completed = completed, total = plan.size))
                        onIndexChanged(
                            SyncIndexDocument.of(
                                providerId = provider.id.id,
                                accountId = accountId,
                                lastSyncedAt = lastSyncedAt,
                                index = updated,
                            ).copy(isRunInProgress = true),
                        )
                    }
                }
            }.awaitAll()
        }
        PassOutcome(summary = summary, index = updated, hasUnresolvedConflicts = hasUnresolvedConflicts)
    }

    private suspend fun runOperation(
        provider: SyncProvider,
        operation: SyncOperation,
        index: Map<SyncKey, SyncIndexEntry>,
        contentHashes: Map<SyncKey, String?>,
    ): OperationOutcome = try {
        when (operation) {
            is SyncOperation.Download -> download(provider, operation, index, contentHashes)
            is SyncOperation.Upload -> upload(provider, operation)
            is SyncOperation.Resolve -> resolve(provider, operation, contentHashes)
            is SyncOperation.DeleteLocal -> deleteLocally(provider, operation, index)

            is SyncOperation.DeleteRemote -> {
                provider.delete(operation.key.kind, operation.key.name, operation.revision)
                OperationOutcome(removals = setOf(operation.key), summary = SyncSummary(deletedRemotely = 1))
            }

            is SyncOperation.Forget -> OperationOutcome(removals = setOf(operation.key))
        }
    } catch (exception: CancellationException) {
        // First, and rethrown: a stopped run is not a file that failed. Caught as an ordinary failure it would fill
        // the log with one line per file still in flight and hide whatever actually ended the run.
        throw exception
    } catch (exception: SyncAuthorizationException) {
        throw exception
    } catch (exception: SyncNetworkException) {
        throw exception
    } catch (exception: Exception) {
        // The index is left alone, so the next run sees this file as it was and tries again.
        println("Could not sync \"${operation.key.path}\": ${exception.message}")
        OperationOutcome()
    }

    /**
     * Overwrites the local file, so it is read, decided about and only then written: the plan was made from hashes
     * taken when the run listed the library, and a long run gives the user plenty of time to save an edit to a song
     * that is still waiting to come down. Checked here, the window in which such a save can be lost is one file's
     * worth of transfer rather than the whole run.
     */
    private suspend fun download(
        provider: SyncProvider,
        operation: SyncOperation.Download,
        index: Map<SyncKey, SyncIndexEntry>,
        contentHashes: Map<SyncKey, String?>,
    ): OperationOutcome {
        val key = operation.key
        // The two sides may already hold the same bytes - two devices given the same file, or a library that was
        // copied across by hand before sync was set up. Nothing has to travel for that, only the index.
        val local = libraryFileLocalSource.readLibraryFile(key.kind, key.name)
        if (local != null && isSameContent(provider, local, contentHashes[key])) {
            return OperationOutcome(entries = mapOf(key to SyncIndexEntry(localContentHash(local), operation.revision)))
        }
        val indexEntry = index[key]
        if (local != null && indexEntry != null && localContentHash(local) != indexEntry.localHash) {
            // Edited since the listing: this is now a file changed on both sides, and it is resolved as one.
            return resolve(provider, SyncOperation.Resolve(key, operation.revision), contentHashes)
        }
        val bytes = provider.download(key.kind, key.name)
        libraryFileLocalSource.writeLibraryFile(key.kind, key.name, bytes)
        return OperationOutcome(
            entries = mapOf(key to SyncIndexEntry(localContentHash(bytes), operation.revision)),
            summary = SyncSummary(downloaded = 1),
        )
    }

    /**
     * Read, decided about and only then deleted, for the same reason as [download]: the plan saw the file unchanged,
     * which says nothing about the minutes the run has taken since. An edit made in between beats the deletion, the
     * same rule [SyncPlanner] applies, and puts the file back on the remote.
     */
    private suspend fun deleteLocally(
        provider: SyncProvider,
        operation: SyncOperation.DeleteLocal,
        index: Map<SyncKey, SyncIndexEntry>,
    ): OperationOutcome {
        val key = operation.key
        val local = libraryFileLocalSource.readLibraryFile(key.kind, key.name) ?: return OperationOutcome(removals = setOf(key))
        if (localContentHash(local) != index[key]?.localHash) {
            return upload(provider, SyncOperation.Upload(key, expectedRevision = null))
        }
        libraryFileLocalSource.deleteLibraryFile(key.kind, key.name)
        return OperationOutcome(removals = setOf(key), summary = SyncSummary(deletedLocally = 1))
    }

    private suspend fun upload(provider: SyncProvider, operation: SyncOperation.Upload): OperationOutcome {
        val key = operation.key
        // Deleted between the listing and now, which the next run will see as a deletion and handle properly.
        val bytes = libraryFileLocalSource.readLibraryFile(key.kind, key.name) ?: return OperationOutcome()
        return when (val result = provider.upload(key.kind, key.name, bytes, operation.expectedRevision)) {
            is RemoteWriteResult.Written -> OperationOutcome(
                entries = mapOf(key to SyncIndexEntry(localContentHash(bytes), result.revision)),
                summary = SyncSummary(uploaded = 1),
            )

            // The remote file moved under the write, which asks for another pass over a fresh listing.
            RemoteWriteResult.Conflict -> OperationOutcome(hasUnresolvedConflict = true)
        }
    }

    /**
     * A file that changed on both sides. The local version keeps the name and goes up; the remote one comes down
     * next to it under a free name and goes back up under that name, so that both devices end with both versions
     * and the same two names. Nothing is merged, and nothing is thrown away.
     *
     * The local version goes up before the copy is written, because the upload is what can still turn out to be
     * contested: a second device resolving the same file at the same moment makes it a conflict, and a copy already
     * on disk by then would be uploaded as a new file on the next pass while the file itself was resolved again into
     * a third one. Written only once the upload has gone through, the copy lands in the pass in which it really is
     * the version that lost.
     */
    private suspend fun resolve(
        provider: SyncProvider,
        operation: SyncOperation.Resolve,
        contentHashes: Map<SyncKey, String?>,
    ): OperationOutcome {
        val key = operation.key
        val local = libraryFileLocalSource.readLibraryFile(key.kind, key.name) ?: return OperationOutcome()
        // The two sides changed to the same thing, which is not a conflict at all - the same edit made twice.
        if (isSameContent(provider, local, contentHashes[key])) {
            return OperationOutcome(entries = mapOf(key to SyncIndexEntry(localContentHash(local), operation.revision)))
        }
        val remote = provider.download(key.kind, key.name)
        if (remote.contentEquals(local)) {
            return OperationOutcome(entries = mapOf(key to SyncIndexEntry(localContentHash(local), operation.revision)))
        }
        val uploaded = provider.upload(key.kind, key.name, local, operation.revision)
        if (uploaded !is RemoteWriteResult.Written) return OperationOutcome(hasUnresolvedConflict = true)
        val entries = mutableMapOf(key to SyncIndexEntry(localContentHash(local), uploaded.revision))

        val copyName = libraryFileLocalSource.writeLibraryFileToFreeName(key.kind, key.name, remote)
        val copyKey = SyncKey(kind = key.kind, name = copyName)
        val copyUploaded = provider.upload(copyKey.kind, copyKey.name, remote, expectedRevision = null)
        if (copyUploaded is RemoteWriteResult.Written) {
            entries[copyKey] = SyncIndexEntry(localContentHash(remote), copyUploaded.revision)
        }
        // Counted as one upload even when the copy also went up: what the user needs to know is that one file was
        // in two states, and that both of them survived under the name in the summary.
        return OperationOutcome(
            entries = entries,
            summary = SyncSummary(uploaded = 1, downloaded = 1, conflicts = listOf(copyName)),
        )
    }

    private fun isSameContent(provider: SyncProvider, local: ByteArray, remoteContentHash: String?) =
        remoteContentHash != null && provider.contentHashOf(local) == remoteContentHash

    private val SyncOperation.order
        get() = when (this) {
            is SyncOperation.Resolve -> 0
            is SyncOperation.Download -> 1
            is SyncOperation.Upload -> 2
            is SyncOperation.DeleteLocal -> 3
            is SyncOperation.DeleteRemote -> 4
            is SyncOperation.Forget -> 5
        }

    private operator fun SyncSummary.plus(other: SyncSummary) = SyncSummary(
        downloaded = downloaded + other.downloaded,
        uploaded = uploaded + other.uploaded,
        deletedLocally = deletedLocally + other.deletedLocally,
        deletedRemotely = deletedRemotely + other.deletedRemotely,
        conflicts = conflicts + other.conflicts,
    )

    data class Result(
        val summary: SyncSummary,
        val index: SyncIndexDocument,
    )

    /** What one operation changed, merged into the pass by whoever finishes first. */
    private data class OperationOutcome(
        val entries: Map<SyncKey, SyncIndexEntry> = emptyMap(),
        val removals: Set<SyncKey> = emptySet(),
        val summary: SyncSummary = SyncSummary(),
        val hasUnresolvedConflict: Boolean = false,
    )

    private data class PassOutcome(
        val summary: SyncSummary,
        val index: Map<SyncKey, SyncIndexEntry>,
        val hasUnresolvedConflicts: Boolean,
    )

    private companion object {
        const val MAXIMUM_PASSES = 2

        /** How many library files are read and hashed at once while the run is preparing. */
        const val READ_BATCH_SIZE = 64

        /**
         * Chosen for the round trip rather than for the CPU: the transfers are small text files and almost all of
         * the time is spent waiting on the network, so this is about how many answers can be in flight before the
         * service starts rate limiting instead.
         */
        const val CONCURRENT_TRANSFERS = 6
    }
}
