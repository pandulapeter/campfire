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

import com.pandulapeter.campfire.data.model.domain.SyncDeletionPolicy
import com.pandulapeter.campfire.data.model.domain.SyncProgress
import com.pandulapeter.campfire.data.model.domain.SyncSummary
import com.pandulapeter.campfire.data.source.local.api.LibraryFileLocalSource
import com.pandulapeter.campfire.data.source.remote.api.SyncAuthorizationException
import com.pandulapeter.campfire.data.source.remote.api.SyncNetworkException
import com.pandulapeter.campfire.data.source.remote.api.SyncProvider
import com.pandulapeter.campfire.data.source.remote.api.SyncRemoteStorageFullException
import com.pandulapeter.campfire.data.source.remote.api.hashing.localContentHash
import com.pandulapeter.campfire.data.source.remote.api.model.RemoteFile
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
 * the service resolves to the same file. Two *local* names that differ only by case cannot both exist on such a
 * service; the one it refuses is reported as a file that could not be synced, see `upload`.
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
 * from travelling, so only the failures that make every further call pointless - the credentials being refused, the
 * service being unreachable, the remote folder being full - stop it. A file that failed is named in the summary,
 * because a run that says nothing about it reads as a backup that works.
 */
internal class SyncEngine(
    private val libraryFileLocalSource: LibraryFileLocalSource,
) {

    suspend fun synchronize(
        provider: SyncProvider,
        document: SyncIndexDocument,
        accountId: String,
        onProgress: (SyncProgress) -> Unit,
        onIndexChanged: suspend (snapshot: () -> SyncIndexDocument) -> Unit,
        deletionPolicy: SyncDeletionPolicy,
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
            val listed = provider.list().files
            index = withoutForeignEntries(index = index, listed = listed)
            val local = readLocalStates()
            // Names a service that ignores case takes for one file. Only ever consulted once such a service has refused
            // one of them, so a service with exact names never notices.
            val caseCollisions = local.groupBy { it.key.folded() }.values
                .filter { it.size > 1 }
                .flatten()
                .mapTo(mutableSetOf()) { it.key }
            val remote = foldRemoteNamesOntoLocal(
                local = local,
                // The rule the local listing applies, applied here rather than in a provider so that every provider gets
                // it: a file listed on one side only is a deletion as far as the planner can tell.
                remote = listed.filter { it.kind.matches(it.name) }.map {
                    RemoteFileState(
                        key = SyncKey(kind = it.kind, name = it.name),
                        revision = it.revision,
                        contentHash = it.contentHash,
                        size = it.size,
                    )
                },
            )
            if (deletionPolicy == SyncDeletionPolicy.KEEP_AND_UPLOAD) {
                // Forgetting that the last run saw these files is what makes them new on this device: a file that is
                // here, is not there and has no index entry is planned as an upload.
                val localKeys = local.mapTo(mutableSetOf()) { it.key }
                val remoteKeys = remote.mapTo(mutableSetOf()) { it.key }
                index = index.filterKeys { it !in localKeys || it in remoteKeys }
            }
            val plan = SyncPlanner.plan(local = local, remote = remote, index = index)
            // Which is what most runs find, so nothing below this costs anything on an ordinary launch.
            if (plan.isEmpty()) break
            val deletions = plan.count { it is SyncOperation.DeleteLocal }
            if (deletionPolicy == SyncDeletionPolicy.ASK && index.isNotEmpty() && deletions.isTooManyToDeleteOutOf(index.size)) {
                return Result.DeletionsNeedConfirmation(count = deletions, total = index.size)
            }
            val outcome = apply(
                provider = provider,
                plan = plan,
                index = index,
                remoteFiles = remote.associateBy { it.key },
                caseCollisions = caseCollisions,
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

        return Result.Completed(
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
     * order they were taken and the last one handed out is always the most complete. What is handed out is a way to
     * take the snapshot rather than the snapshot, since building one costs as much as the index is long and most of
     * them are never written. It reads the pass's own map, so it may be called in exactly two places: inside
     * [onIndexChanged], which runs under the lock, and after [synchronize] has returned or thrown, when nothing writes
     * to that map any more. Never from a coroutine launched out of [onIndexChanged].
     */
    private suspend fun apply(
        provider: SyncProvider,
        plan: List<SyncOperation>,
        index: Map<SyncKey, SyncIndexEntry>,
        remoteFiles: Map<SyncKey, RemoteFileState>,
        caseCollisions: Set<SyncKey>,
        onProgress: (SyncProgress) -> Unit,
        accountId: String,
        lastSyncedAt: Long,
        onIndexChanged: suspend (snapshot: () -> SyncIndexDocument) -> Unit,
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
                    val outcome = permits.withPermit { runOperation(provider, operation, index, remoteFiles, caseCollisions) }
                    results.withLock {
                        updated += outcome.entries
                        updated -= outcome.removals
                        summary = summary.plus(outcome.summary)
                        hasUnresolvedConflicts = hasUnresolvedConflicts || outcome.hasUnresolvedConflict
                        completed++
                        onProgress(SyncProgress(completed = completed, total = plan.size))
                        onIndexChanged {
                            SyncIndexDocument.of(
                                providerId = provider.id.id,
                                accountId = accountId,
                                lastSyncedAt = lastSyncedAt,
                                index = updated,
                            ).copy(isRunInProgress = true)
                        }
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
        remoteFiles: Map<SyncKey, RemoteFileState>,
        caseCollisions: Set<SyncKey>,
    ): OperationOutcome = try {
        when (operation) {
            is SyncOperation.Download -> download(provider, operation, index, remoteFiles)
            is SyncOperation.Upload -> upload(provider, operation, caseCollisions)
            is SyncOperation.Resolve -> resolve(provider, operation, remoteFiles)
            is SyncOperation.DeleteLocal -> deleteLocally(provider, operation, index, caseCollisions)

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
    } catch (exception: SyncRemoteStorageFullException) {
        throw exception
    } catch (exception: Exception) {
        // The index is left alone, so the next run sees this file as it was and tries again.
        println("Could not sync \"${operation.key.path}\": ${exception.message}")
        OperationOutcome(summary = SyncSummary(failed = listOf(operation.key.name)))
    }

    /**
     * Overwrites the local file, so it is read, decided about and only then written: the plan was made from hashes
     * taken when the run listed the library, and a long run gives the user plenty of time to save an edit to a song
     * that is still waiting to come down. The same goes for a file that was not there at all when the run listed the
     * library and is now: nothing planned for this name knew about it, so it is never written over. Checked here, the
     * window in which such a save can be lost is one file's worth of transfer rather than the whole run.
     */
    private suspend fun download(
        provider: SyncProvider,
        operation: SyncOperation.Download,
        index: Map<SyncKey, SyncIndexEntry>,
        remoteFiles: Map<SyncKey, RemoteFileState>,
    ): OperationOutcome {
        val key = operation.key
        // The two sides may already hold the same bytes - two devices given the same file, or a library that was
        // copied across by hand before sync was set up. Nothing has to travel for that, only the index.
        val local = libraryFileLocalSource.readLibraryFile(key.kind, key.name)
        if (local != null && isSameContent(provider, local, remoteFiles[key]?.contentHash)) {
            return OperationOutcome(entries = mapOf(key to SyncIndexEntry(localContentHash(local), operation.revision)))
        }
        if (local != null && localContentHash(local) != index[key]?.localHash) {
            // Not the file the last run saw. With an index entry it was edited since the listing; with none it was not
            // there at all when the run listed the library, so it appeared since - a conflict copy written earlier in
            // this pass, or a song the user made while the run was going. Either way it has changed on both sides, and
            // it is resolved as that rather than written over.
            return resolve(provider, SyncOperation.Resolve(key, operation.revision), remoteFiles)
        }
        val bytes = downloadWithinLimit(provider, key, remoteFiles)
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
        caseCollisions: Set<SyncKey>,
    ): OperationOutcome {
        val key = operation.key
        val local = libraryFileLocalSource.readLibraryFile(key.kind, key.name) ?: return OperationOutcome(removals = setOf(key))
        if (localContentHash(local) != index[key]?.localHash) {
            return upload(provider, SyncOperation.Upload(key, expectedRevision = null), caseCollisions)
        }
        libraryFileLocalSource.deleteLibraryFile(key.kind, key.name)
        return OperationOutcome(removals = setOf(key), summary = SyncSummary(deletedLocally = 1))
    }

    private suspend fun upload(
        provider: SyncProvider,
        operation: SyncOperation.Upload,
        caseCollisions: Set<SyncKey>,
    ): OperationOutcome {
        val key = operation.key
        // Deleted between the listing and now, which the next run will see as a deletion and handle properly.
        val bytes = libraryFileLocalSource.readLibraryFile(key.kind, key.name) ?: return OperationOutcome()
        return when (val result = provider.upload(key.kind, key.name, bytes, operation.expectedRevision)) {
            is RemoteWriteResult.Written -> OperationOutcome(
                entries = mapOf(key to SyncIndexEntry(localContentHash(bytes), result.revision)),
                summary = SyncSummary(uploaded = 1),
            )

            RemoteWriteResult.Conflict -> if (operation.expectedRevision == null && key in caseCollisions) {
                // Refused because the service already holds this name in another spelling, which is the other local file.
                // Another pass would be refused the same way, so this is a file that could not be synced rather than a
                // conflict waiting to be resolved.
                println("Could not sync \"${key.path}\": the service holds the same name in another case.")
                OperationOutcome(summary = SyncSummary(failed = listOf(key.name)))
            } else {
                // The remote file moved under the write, which asks for another pass over a fresh listing.
                OperationOutcome(hasUnresolvedConflict = true)
            }
        }
    }

    /**
     * A file that changed on both sides. The local version keeps the name and goes up; the remote one comes down
     * next to it under a free name and goes back up under that name, so that both devices end with both versions
     * and the same two names. Nothing is merged, and nothing is thrown away.
     *
     * The incoming version is on disk before the local one goes up, because going up is what destroys it on the
     * remote: held only in memory across that request, a copy that then cannot be written - a full disk, a process
     * that is killed - would be a version that exists nowhere. The upload can still turn out to be contested, a second
     * device resolving the same file in the same moment, and a copy left on disk then would go up as a new file on the
     * next pass while the file itself was resolved again into another one. So the copy is taken back whenever the
     * service has said that the remote version is still there: a refusal, or a conflict that is not the echo of this
     * device's own write. Where nothing says either way - the network dropped, the run was stopped - it stays, and the
     * worst that follows is a second identical copy.
     */
    private suspend fun resolve(
        provider: SyncProvider,
        operation: SyncOperation.Resolve,
        remoteFiles: Map<SyncKey, RemoteFileState>,
    ): OperationOutcome {
        val key = operation.key
        val local = libraryFileLocalSource.readLibraryFile(key.kind, key.name) ?: return OperationOutcome()
        // The two sides changed to the same thing, which is not a conflict at all - the same edit made twice.
        if (isSameContent(provider, local, remoteFiles[key]?.contentHash)) {
            return OperationOutcome(entries = mapOf(key to SyncIndexEntry(localContentHash(local), operation.revision)))
        }
        val remote = downloadWithinLimit(provider, key, remoteFiles)
        if (remote.contentEquals(local)) {
            return OperationOutcome(entries = mapOf(key to SyncIndexEntry(localContentHash(local), operation.revision)))
        }
        val copyName = libraryFileLocalSource.writeLibraryFileToFreeName(key.kind, key.name, remote)
        val copyKey = SyncKey(kind = key.kind, name = copyName)
        val uploaded = try {
            provider.upload(key.kind, key.name, local, operation.revision)
        } catch (exception: CancellationException) {
            // Nothing says whether the write landed, so the copy stays: see the KDoc.
            throw exception
        } catch (exception: SyncNetworkException) {
            throw exception
        } catch (exception: Exception) {
            // The service answered, and the answer was no. The remote version is where it was, and a copy kept now would
            // be joined by another one every time this file is resolved again.
            discardCopy(copyKey, remote)
            throw exception
        }
        if (uploaded !is RemoteWriteResult.Written) {
            // Contested - unless what is there now is what was just sent, which is how a write looks that landed and was
            // then retried. In that case the remote version is gone from the service, and the copy is all there is of it.
            val isOwnWrite = provider.download(key.kind, key.name).contentEquals(local)
            if (!isOwnWrite) discardCopy(copyKey, remote)
            return OperationOutcome(
                summary = if (isOwnWrite) SyncSummary(conflicts = listOf(copyName)) else SyncSummary(),
                hasUnresolvedConflict = true,
            )
        }
        val entries = mutableMapOf(key to SyncIndexEntry(localContentHash(local), uploaded.revision))
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

    /**
     * Takes back a copy whose file turned out not to have been overwritten remotely. Read before it is deleted, like
     * everything else this class removes: only the bytes that were written a moment ago are taken back.
     */
    private suspend fun discardCopy(copyKey: SyncKey, written: ByteArray) {
        try {
            if (libraryFileLocalSource.readLibraryFile(copyKey.kind, copyKey.name)?.contentEquals(written) == true) {
                libraryFileLocalSource.deleteLibraryFile(copyKey.kind, copyKey.name)
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            // A copy too many is the harmless way for this to go wrong.
            println("Could not remove the unused copy \"${copyKey.path}\": ${exception.message}")
        }
    }

    private fun isSameContent(provider: SyncProvider, local: ByteArray, remoteContentHash: String?) =
        remoteContentHash != null && provider.contentHashOf(local) == remoteContentHash

    /**
     * A song is a few kilobytes of text, and a download is held in memory whole. Refused here rather than left out
     * of the listing: a file the planner cannot see on the remote is a file it takes for deleted there, and a
     * large one that is already in the library would be deleted locally for it. Thrown, it is one file's failure
     * like any other - logged, the index left alone, tried again by the next run.
     */
    private suspend fun downloadWithinLimit(
        provider: SyncProvider,
        key: SyncKey,
        remoteFiles: Map<SyncKey, RemoteFileState>,
    ): ByteArray {
        val size = remoteFiles[key]?.size ?: 0
        if (size > MAXIMUM_REMOTE_FILE_SIZE) throw RemoteFileTooLargeException(size)
        return provider.download(key.kind, key.name)
    }

    /**
     * Drops the index entries of files that are not library files, which only an index from before the remote
     * listing was filtered can hold: such a file was downloaded into the library folder, where nothing lists it.
     *
     * That copy is deleted where it is provably only a copy - still the bytes the index recorded, with the remote
     * file still there at the revision they came from. Anything else is left alone. In particular a foreign file
     * with *no* index entry may be the last copy of something, and cannot be told from a file the user put into
     * the library folder themselves.
     */
    private suspend fun withoutForeignEntries(
        index: Map<SyncKey, SyncIndexEntry>,
        listed: List<RemoteFile>,
    ): Map<SyncKey, SyncIndexEntry> {
        val foreign = index.filterKeys { !it.kind.matches(it.name) }
        foreign.forEach { (key, entry) ->
            val isStillRemote = listed.any { it.kind == key.kind && it.name == key.name && it.revision == entry.remoteRevision }
            try {
                val local = libraryFileLocalSource.readLibraryFile(key.kind, key.name)
                if (isStillRemote && local != null && localContentHash(local) == entry.localHash) {
                    libraryFileLocalSource.deleteLibraryFile(key.kind, key.name)
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                // Tidying up is not worth a run: the entry is dropped either way, and the file stays where it is.
                println("Could not remove the local copy of \"${key.path}\": ${exception.message}")
            }
        }
        return index - foreign.keys
    }

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
        failed = (failed + other.failed).distinct(),
    )

    /**
     * Whether a plan deletes enough of what the last run saw that it is more likely a remote folder that was emptied,
     * renamed or replaced than songs somebody deleted. Without this, every device would carry out such a folder's
     * absence faithfully, keeping only what had been edited since the last run - and edit-beats-deletion is exactly
     * what would make that loss quiet. More than half is the threshold, with [MIN_DELETIONS_TO_ASK] so that tidying
     * up a small library does not ask every time, except where the plan deletes everything the index knows.
     */
    private fun Int.isTooManyToDeleteOutOf(total: Int) =
        this > 0 && (this == total || (this >= MIN_DELETIONS_TO_ASK && this * 2 > total))

    sealed interface Result {

        data class Completed(
            val summary: SyncSummary,
            val index: SyncIndexDocument,
        ) : Result

        /** Nothing moved: the plan would have deleted [count] of the [total] files the index knows, and asks first. */
        data class DeletionsNeedConfirmation(
            val count: Int,
            val total: Int,
        ) : Result
    }

    /** What one operation changed, merged into the pass by whoever finishes first. */
    private data class OperationOutcome(
        val entries: Map<SyncKey, SyncIndexEntry> = emptyMap(),
        val removals: Set<SyncKey> = emptySet(),
        val summary: SyncSummary = SyncSummary(),
        val hasUnresolvedConflict: Boolean = false,
    )

    private class RemoteFileTooLargeException(size: Long) :
        Exception("The remote file is $size bytes, which is more than the $MAXIMUM_REMOTE_FILE_SIZE a run downloads.")

    private data class PassOutcome(
        val summary: SyncSummary,
        val index: Map<SyncKey, SyncIndexEntry>,
        val hasUnresolvedConflicts: Boolean,
    )

    private companion object {
        const val MAXIMUM_PASSES = 2

        /** The fewest local deletions that can stop a run, unless they are the whole library, see [isTooManyToDeleteOutOf]. */
        const val MIN_DELETIONS_TO_ASK = 5

        /** How many library files are read and hashed at once while the run is preparing. */
        const val READ_BATCH_SIZE = 64

        /**
         * The largest remote file a run downloads: what the largest song an import reads comes to, so that a song that
         * could be brought into one library can reach the others. Generous for ChordPro text, and small enough that
         * [CONCURRENT_TRANSFERS] of them in memory at once do not trouble a phone.
         */
        const val MAXIMUM_REMOTE_FILE_SIZE = 8L shl 20

        /**
         * Chosen for the round trip rather than for the CPU: the transfers are small text files and almost all of
         * the time is spent waiting on the network, so this is about how many answers can be in flight before the
         * service starts rate limiting instead.
         */
        const val CONCURRENT_TRANSFERS = 6
    }
}
