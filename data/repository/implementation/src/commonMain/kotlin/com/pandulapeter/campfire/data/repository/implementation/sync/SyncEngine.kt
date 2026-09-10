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
 * Carries out what [SyncPlanner] worked out.
 *
 * Everything here is written so that a run which is interrupted - the network drops, the app is killed, the user
 * stops it - leaves the library usable and the next run able to pick up: the index is only told about a file once
 * that file has actually moved, so anything half done simply looks unsynced next time rather than done.
 *
 * A failure on one file does not end the run. A song the storage cannot read must not keep the other four hundred
 * from travelling, so only the two failures that make every further call pointless - the credentials being refused
 * and the service being unreachable - stop it.
 */
internal class SyncEngine(
    private val libraryFileLocalSource: LibraryFileLocalSource
) {

    suspend fun synchronize(
        provider: SyncProvider,
        document: SyncIndexDocument,
        accountId: String,
        onProgress: (SyncProgress) -> Unit
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
            val plan = SyncPlanner.plan(
                local = readLocalStates(),
                remote = files.map {
                    RemoteFileState(
                        key = SyncKey(kind = it.kind, name = it.name),
                        revision = it.revision,
                        contentHash = it.contentHash
                    )
                },
                index = index
            )
            // Which is what most runs find, so nothing below this costs anything on an ordinary launch.
            if (plan.isEmpty()) break
            val outcome = apply(
                provider = provider,
                plan = plan,
                index = index,
                contentHashes = files.associate { SyncKey(kind = it.kind, name = it.name) to it.contentHash },
                onProgress = onProgress
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
                index = index
            )
        )
    }

    /** Reading and hashing every file is the slow part of the preparation, so the files are read in parallel. */
    private suspend fun readLocalStates(): List<LocalFileState> = coroutineScope {
        libraryFileLocalSource.loadLibraryFiles()
            .map { file ->
                async {
                    val key = SyncKey(kind = file.kind, name = file.name)
                    libraryFileLocalSource.readLibraryFile(file.kind, file.name)
                        ?.let { LocalFileState(key = key, hash = localContentHash(it)) }
                }
            }
            .awaitAll()
            .filterNotNull()
    }

    /**
     * Every operation is at least one request of its own, and they used to be run one after another - which made a
     * first sync of a few hundred songs a few hundred round trips end to end, and the slowest thing the app does by
     * a wide margin. Files do not depend on each other, so they go at once, [CONCURRENT_TRANSFERS] at a time: enough
     * to hide the latency, few enough that the service answers with files rather than with rate limiting.
     *
     * The ordering that does matter is kept between the groups: incoming files first, then outgoing ones, then the
     * deletions. A conflict copy has to be on disk before the file it was made from is overwritten, and a deletion
     * that ran before a download would undo it.
     */
    private suspend fun apply(
        provider: SyncProvider,
        plan: List<SyncOperation>,
        index: Map<SyncKey, SyncIndexEntry>,
        contentHashes: Map<SyncKey, String?>,
        onProgress: (SyncProgress) -> Unit
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
                    val outcome = permits.withPermit { runOperation(provider, operation, contentHashes) }
                    results.withLock {
                        updated += outcome.entries
                        updated -= outcome.removals
                        summary = summary.plus(outcome.summary)
                        hasUnresolvedConflicts = hasUnresolvedConflicts || outcome.hasUnresolvedConflict
                        completed++
                        onProgress(SyncProgress(completed = completed, total = plan.size))
                    }
                }
            }.awaitAll()
        }
        PassOutcome(summary = summary, index = updated, hasUnresolvedConflicts = hasUnresolvedConflicts)
    }

    private suspend fun runOperation(
        provider: SyncProvider,
        operation: SyncOperation,
        contentHashes: Map<SyncKey, String?>
    ): OperationOutcome = try {
        when (operation) {
            is SyncOperation.Download -> download(provider, operation, contentHashes)
            is SyncOperation.Upload -> upload(provider, operation)
            is SyncOperation.Resolve -> resolve(provider, operation, contentHashes)

            is SyncOperation.DeleteLocal -> {
                libraryFileLocalSource.deleteLibraryFile(operation.key.kind, operation.key.name)
                OperationOutcome(removals = setOf(operation.key), summary = SyncSummary(deletedLocally = 1))
            }

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

    private suspend fun download(
        provider: SyncProvider,
        operation: SyncOperation.Download,
        contentHashes: Map<SyncKey, String?>
    ): OperationOutcome {
        val key = operation.key
        // The two sides may already hold the same bytes - two devices given the same file, or a library that was
        // copied across by hand before sync was set up. Nothing has to travel for that, only the index.
        val local = libraryFileLocalSource.readLibraryFile(key.kind, key.name)
        if (local != null && isSameContent(provider, local, contentHashes[key])) {
            return OperationOutcome(entries = mapOf(key to SyncIndexEntry(localContentHash(local), operation.revision)))
        }
        val bytes = provider.download(key.kind, key.name)
        libraryFileLocalSource.writeLibraryFile(key.kind, key.name, bytes)
        return OperationOutcome(
            entries = mapOf(key to SyncIndexEntry(localContentHash(bytes), operation.revision)),
            summary = SyncSummary(downloaded = 1)
        )
    }

    private suspend fun upload(provider: SyncProvider, operation: SyncOperation.Upload): OperationOutcome {
        val key = operation.key
        // Deleted between the listing and now, which the next run will see as a deletion and handle properly.
        val bytes = libraryFileLocalSource.readLibraryFile(key.kind, key.name) ?: return OperationOutcome()
        return when (val result = provider.upload(key.kind, key.name, bytes, operation.expectedRevision)) {
            is RemoteWriteResult.Written -> OperationOutcome(
                entries = mapOf(key to SyncIndexEntry(localContentHash(bytes), result.revision)),
                summary = SyncSummary(uploaded = 1)
            )

            // The remote file moved under the write, which asks for another pass over a fresh listing.
            RemoteWriteResult.Conflict -> OperationOutcome(hasUnresolvedConflict = true)
        }
    }

    /**
     * A file that changed on both sides. The local version keeps the name and goes up; the remote one comes down
     * next to it under a free name and goes back up under that name, so that both devices end with both versions
     * and the same two names. Nothing is merged, and nothing is thrown away.
     */
    private suspend fun resolve(
        provider: SyncProvider,
        operation: SyncOperation.Resolve,
        contentHashes: Map<SyncKey, String?>
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
        val copyName = libraryFileLocalSource.writeLibraryFileToFreeName(key.kind, key.name, remote)
        val copyKey = SyncKey(kind = key.kind, name = copyName)

        val uploaded = provider.upload(key.kind, key.name, local, operation.revision)
        if (uploaded !is RemoteWriteResult.Written) return OperationOutcome(hasUnresolvedConflict = true)
        val entries = mutableMapOf(key to SyncIndexEntry(localContentHash(local), uploaded.revision))

        val copyUploaded = provider.upload(copyKey.kind, copyKey.name, remote, expectedRevision = null)
        if (copyUploaded is RemoteWriteResult.Written) {
            entries[copyKey] = SyncIndexEntry(localContentHash(remote), copyUploaded.revision)
        }
        // Counted as one upload even when the copy also went up: what the user needs to know is that one file was
        // in two states, and that both of them survived under the name in the summary.
        return OperationOutcome(
            entries = entries,
            summary = SyncSummary(uploaded = 1, downloaded = 1, conflicts = listOf(copyName))
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
        conflicts = conflicts + other.conflicts
    )

    data class Result(
        val summary: SyncSummary,
        val index: SyncIndexDocument
    )

    /** What one operation changed, merged into the pass by whoever finishes first. */
    private data class OperationOutcome(
        val entries: Map<SyncKey, SyncIndexEntry> = emptyMap(),
        val removals: Set<SyncKey> = emptySet(),
        val summary: SyncSummary = SyncSummary(),
        val hasUnresolvedConflict: Boolean = false
    )

    private data class PassOutcome(
        val summary: SyncSummary,
        val index: Map<SyncKey, SyncIndexEntry>,
        val hasUnresolvedConflicts: Boolean
    )

    private companion object {
        const val MAXIMUM_PASSES = 2

        /**
         * Chosen for the round trip rather than for the CPU: the transfers are small text files and almost all of
         * the time is spent waiting on the network, so this is about how many answers can be in flight before the
         * service starts rate limiting instead.
         */
        const val CONCURRENT_TRANSFERS = 6
    }
}
