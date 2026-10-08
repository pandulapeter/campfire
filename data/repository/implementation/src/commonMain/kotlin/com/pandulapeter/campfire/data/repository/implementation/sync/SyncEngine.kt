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

import com.pandulapeter.campfire.data.model.domain.ImportLimits
import com.pandulapeter.campfire.data.model.domain.Logger
import com.pandulapeter.campfire.data.model.domain.SyncDeletionDirection
import com.pandulapeter.campfire.data.model.domain.SyncDeletionPolicy
import com.pandulapeter.campfire.data.model.domain.SyncProgress
import com.pandulapeter.campfire.data.model.domain.SyncSummary
import com.pandulapeter.campfire.data.repository.implementation.LibraryFileLock
import com.pandulapeter.campfire.data.repository.implementation.base.recovering
import com.pandulapeter.campfire.data.source.local.api.LibraryFileLocalSource
import com.pandulapeter.campfire.data.source.local.api.SetlistComparison
import com.pandulapeter.campfire.data.source.remote.api.SyncFolder
import com.pandulapeter.campfire.data.source.remote.api.SyncRunEndingException
import com.pandulapeter.campfire.data.source.remote.api.hashing.localContentHash
import com.pandulapeter.campfire.data.source.remote.api.model.RemoteDeletion
import com.pandulapeter.campfire.data.source.remote.api.model.RemoteDocument
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
 * from travelling: it is left out of the plan on both sides, its index entry kept for the run that can read it, and
 * named in the summary. Only the failures that make every further call pointless - the credentials being refused, the
 * service being unreachable, the remote folder being full - stop it. A file that failed is named in the summary,
 * because a run that says nothing about it reads as a backup that works.
 *
 * Every change this makes to a library file is decided about and carried out under [libraryFileLock], the lock the
 * song and setlist repositories write under, and nothing else is: a request is never made while it is held. Each one is
 * reported to [synchronize]'s `onLocalFileChanged` once it is on disk, and not before: whoever re-reads the file for it
 * must not be able to read it ahead of the change. That is what lets the repositories re-read the files a run touched
 * rather than the whole library.
 *
 * [setlistComparison] is the one look this class takes inside a file: a setlist whose two versions differ only in the
 * day they name is one setlist rather than a conflict, see [ConflictResolver.resolveWith]. [plantedContentHash] is the
 * other exception to keeping both: a demo file this device planted and nobody touched since yields to the cloud
 * folder's version. Both only reach the [ConflictResolver] the engine builds with them.
 */
internal class SyncEngine(
    private val libraryFileLocalSource: LibraryFileLocalSource,
    private val libraryFileLock: LibraryFileLock,
    setlistComparison: SetlistComparison,
    /**
     * What the app planted under a name, as a content hash, or null for a file it did not plant; see
     * [ConflictResolver.resolveWith].
     */
    plantedContentHash: suspend (SyncKey) -> String? = { null },
    /** Where the failures of single files are written. A default for the tests' direct calls; production passes one. */
    private val logger: Logger = Logger.Standard,
) {

    private val conflictResolver = ConflictResolver(libraryFileLocalSource, libraryFileLock, setlistComparison, plantedContentHash, logger)

    suspend fun synchronize(
        provider: SyncFolder,
        document: SyncIndexDocument,
        accountId: String,
        onProgress: (SyncProgress) -> Unit,
        onIndexChanged: suspend (snapshot: () -> SyncIndexDocument) -> Unit,
        onLocalFileChanged: suspend (SyncKey) -> Unit,
        deletionPolicy: SyncDeletionPolicy,
    ): Result {
        // An index written for a different account describes a different remote folder, and acting on it would read
        // that folder's absent files as deletions of this one's songs.
        var index = document.takeIf { it.accountId == accountId }?.toIndex().orEmpty()
        var syncedPreferences = document.syncedPreferences.takeIf { document.accountId == accountId }
        var summary = SyncSummary()
        val run = SyncRun(
            provider = provider,
            accountId = accountId,
            lastSyncedAt = document.lastSyncedAt,
            onProgress = onProgress,
            onIndexChanged = onIndexChanged,
            onLocalFileChanged = onLocalFileChanged,
        )

        // Two passes at most. A file that a second device changed between this run's listing and its upload comes
        // back as a conflict; the second pass sees the revision it actually has now and resolves it properly. If it
        // happens again the run stops rather than chasing a device that is writing continuously, and the next sync
        // settles it.
        var pass = 0
        var unresolved = emptySet<SyncKey>()
        while (pass < MAXIMUM_PASSES) {
            // Listing both sides is the part of a run with nothing to show for it yet, so the progress reported
            // here has no total and the indicator spins rather than sitting at zero.
            onProgress(SyncProgress())
            val listed = provider.list().files
            index = withoutForeignEntries(index = index, listed = listed)
            val localListing = readLocalStates()
            val tooLarge = localListing.tooLarge
            val unreadable = localListing.unreadable
            if (pass == 0) summary = summary.plus(SyncSummary(failed = tooLarge.map { it.name }))
            // Every pass: a file can be readable in the first one and not in the second.
            summary = summary.plus(SyncSummary(failed = unreadable.map { it.name }))
            val prepared = preparePass(
                index = index,
                listed = listed,
                local = localListing.states,
                tooLarge = tooLarge,
                unreadable = unreadable,
                canHoldFileName = libraryFileLocalSource::canHoldFileName,
                deletionPolicy = deletionPolicy,
                syncedPreferences = syncedPreferences,
            )
            index = prepared.index
            syncedPreferences = prepared.syncedPreferences
            val local = prepared.local
            val remote = prepared.remote
            val caseCollisions = prepared.caseCollisions
            val planningIndex = prepared.planningIndex
            // Named once per run rather than tried again every time: this device's file system cannot hold the name,
            // which no number of runs will change. Split off before the names are folded, so that such a name is never
            // matched onto a local one either.
            if (pass == 0) summary = summary.plus(SyncSummary(failed = prepared.unstorable))
            val plan = SyncPlanner.plan(local = local, remote = remote, index = planningIndex)
            // Which is what most runs find, so nothing below this costs anything on an ordinary launch.
            if (plan.isEmpty()) {
                unresolved = emptySet()
                break
            }
            DeletionGuard.check(
                plan = plan,
                planningIndexSize = planningIndex.size,
                isLocalListingEmpty = local.isEmpty(),
                policy = deletionPolicy,
            )?.let { return it }
            val outcome = apply(
                pass = SyncPass(
                    run = run,
                    index = index,
                    remoteFiles = remote.associateBy { it.key },
                    caseCollisions = caseCollisions,
                    syncedPreferences = syncedPreferences,
                ),
                plan = plan,
            )
            index = outcome.index
            summary = summary.plus(outcome.summary)
            unresolved = outcome.unresolved
            if (unresolved.isEmpty()) break
            pass++
        }
        // Still contested when the passes ran out, so the two sides differ and nothing this run did settled it. Named
        // the way a file that failed is, since a run that says nothing about it would count as the last successful one
        // and read as a library that is in step.
        summary = summary.plus(SyncSummary(failed = unresolved.map { it.name }))

        return Result.Completed(
            summary = summary,
            index = SyncIndexDocument.of(
                providerId = provider.id.id,
                accountId = accountId,
                lastSyncedAt = document.lastSyncedAt,
                index = index,
                syncedPreferences = syncedPreferences,
            ),
        )
    }

    /**
     * Reading and hashing every file is the slow part of the preparation, so the files are read in parallel. A file
     * over [MAXIMUM_FILE_SIZE] is not read at all: nothing the app writes is that large, so it was put into the folder
     * from outside, and it is no song any other device would download.
     *
     * A file whose read fails is one file's failure, like any other in a run, and is handed back as unreadable rather
     * than left out: left out, it would be a file gone from this device, which the planner carries out as a deletion
     * on every other one. Only a library of which not one file could be read ends the run, since that is the storage
     * failing rather than a file.
     */
    private suspend fun readLocalStates(): LocalListing = coroutineScope {
        val (tooLarge, files) = libraryFileLocalSource.loadLibraryFiles().partition { it.size > MAXIMUM_FILE_SIZE }
        tooLarge.forEach { logger.log("Skipped \"${it.name}\": ${it.size} bytes is more than a library file can hold.") }
        // In batches for the same reason as the song scan in SongLocalSourceImpl: unbounded, a large library is
        // thousands of open handles and all of its bytes in memory at once.
        val reads = files
            .chunked(READ_BATCH_SIZE)
            .flatMap { batch -> batch.map { file -> async { readLocalState(SyncKey(kind = file.kind, name = file.name)) } }.awaitAll() }
        val states = reads.mapNotNull { (it as? LocalRead.Read)?.state }
        val failures = reads.filterIsInstance<LocalRead.Failed>()
        if (states.isEmpty() && failures.isNotEmpty()) throw failures.first().exception
        LocalListing(
            states = states,
            tooLarge = tooLarge.mapTo(mutableSetOf()) { SyncKey(kind = it.kind, name = it.name) },
            unreadable = failures.mapTo(mutableSetOf()) { it.key },
        )
    }

    private suspend fun readLocalState(key: SyncKey): LocalRead = logger.recovering(
        describe = { "Could not read \"${key.path}\": ${it.message}" },
        fallback = { LocalRead.Failed(key, it) },
    ) {
        libraryFileLocalSource.readLibraryFile(key.kind, key.name)
            ?.let { LocalRead.Read(LocalFileState(key = key, hash = localContentHash(it))) }
            ?: LocalRead.Absent
    }

    /** What reading one listed file came to. [Absent] is a file deleted since the listing, which really is gone. */
    private sealed interface LocalRead {
        class Read(val state: LocalFileState) : LocalRead
        data object Absent : LocalRead
        class Failed(val key: SyncKey, val exception: Exception) : LocalRead
    }

    /** What [readLocalStates] found: the files it read, the ones it left alone for their size, and the ones it could not read. */
    private class LocalListing(val states: List<LocalFileState>, val tooLarge: Set<SyncKey>, val unreadable: Set<SyncKey>)

    /**
     * Every operation is at least one request of its own, and they used to be run one after another - which made a
     * first sync of a few hundred songs a few hundred round trips end to end, and the slowest thing the app does by
     * a wide margin. Files do not depend on each other, so they go at once, [CONCURRENT_TRANSFERS] at a time: enough
     * to hide the latency, few enough that the service answers with files rather than with rate limiting.
     *
     * The ordering that does matter is kept between the groups: incoming files first, then outgoing ones, then the
     * deletions. A download has to be on disk before anything that reads the library acts on it, and a deletion that
     * ran before a download would undo it. The remote deletions are the one group that is not spread over the
     * permits: they go to the provider together ([deleteRemotely]).
     *
     * [SyncRun.onIndexChanged] is called under the same lock the results are merged under, so the snapshots arrive in
     * the order they were taken and the last one handed out is always the most complete. What is handed out is a way to
     * take the snapshot rather than the snapshot, since building one costs as much as the index is long and most of
     * them are never written. It reads the pass's own map, so it may be called in exactly two places: inside
     * [SyncRun.onIndexChanged], which runs under the lock, and after [synchronize] has returned or thrown, when nothing
     * writes to that map any more. Never from a coroutine launched out of [SyncRun.onIndexChanged].
     */
    private suspend fun apply(pass: SyncPass, plan: List<SyncOperation>): PassOutcome = coroutineScope {
        val run = pass.run
        val updated = pass.index.toMutableMap()
        var summary = SyncSummary()
        val unresolved = mutableSetOf<SyncKey>()
        var completed = 0
        // Whichever request finishes first writes to all four of those, so the merging is done in one place.
        val results = Mutex()
        val permits = Semaphore(CONCURRENT_TRANSFERS)
        run.onProgress(SyncProgress(completed = 0, total = plan.size))

        suspend fun record(operation: SyncOperation, outcome: OperationOutcome) = results.withLock {
            updated += outcome.entries
            updated -= outcome.removals
            summary = summary.plus(outcome.summary)
            if (outcome.isUnresolved) unresolved += operation.key
            completed++
            run.onProgress(SyncProgress(completed = completed, total = plan.size))
            run.onIndexChanged {
                SyncIndexDocument.of(
                    providerId = run.provider.id.id,
                    accountId = run.accountId,
                    lastSyncedAt = run.lastSyncedAt,
                    index = updated,
                    syncedPreferences = pass.syncedPreferences,
                ).copy(isRunInProgress = true)
            }
        }

        plan.groupBy { it.order }.entries.sortedBy { it.key }.forEach { (_, group) ->
            val deletions = group.filterIsInstance<SyncOperation.DeleteRemote>()
            if (deletions.isNotEmpty()) {
                deleteRemotely(pass, deletions).forEach { (operation, outcome) -> record(operation, outcome) }
            }
            group.filterNot { it is SyncOperation.DeleteRemote }.map { operation ->
                async { record(operation, permits.withPermit { runOperation(pass, operation) }) }
            }.awaitAll()
        }
        PassOutcome(summary = summary, index = updated, unresolved = unresolved)
    }

    private suspend fun runOperation(pass: SyncPass, operation: SyncOperation): OperationOutcome = try {
        when (operation) {
            is SyncOperation.Download -> download(pass, operation)
            is SyncOperation.Upload -> upload(pass, operation)
            is SyncOperation.Resolve -> resolve(pass, operation)
            is SyncOperation.DeleteLocal -> deleteLocally(pass, operation)

            is SyncOperation.DeleteRemote -> deleteRemotely(pass, listOf(operation)).single().second

            is SyncOperation.Forget -> OperationOutcome(removals = setOf(operation.key))
        }
    } catch (exception: Exception) {
        if (exception.endsTheRun) throw exception
        failedOutcome(operation.key, exception.message)
    }

    /**
     * All of a pass's remote deletions go to the provider in one call rather than [CONCURRENT_TRANSFERS] at a time:
     * another device that lists the folder while they are under way decides from what it sees gone whether to ask
     * before following, so the shorter they take, the less of a large deletion reaches it unasked (see
     * [SyncFolder.delete]). What ends a run ends it here too; anything else fails the files it was about.
     */
    private suspend fun deleteRemotely(
        pass: SyncPass,
        operations: List<SyncOperation.DeleteRemote>,
    ): List<Pair<SyncOperation, OperationOutcome>> {
        val deletions = operations.associateBy { RemoteDeletion(kind = it.key.kind, name = it.key.name, expectedRevision = it.revision) }
        val failures = try {
            pass.run.provider.delete(deletions.keys.toList())
        } catch (exception: Exception) {
            if (exception.endsTheRun) throw exception
            deletions.keys.associateWith { exception.message.orEmpty() }
        }
        return deletions.map { (deletion, operation) ->
            operation to when (val reason = failures[deletion]) {
                null -> OperationOutcome(removals = setOf(operation.key), summary = SyncSummary(deletedRemotely = 1))
                else -> failedOutcome(operation.key, reason)
            }
        }
    }

    /** The index is left alone, so the next run sees this file as it was and tries again. */
    private fun failedOutcome(key: SyncKey, reason: String?): OperationOutcome {
        logger.log("Could not sync \"${key.path}\": $reason")
        return OperationOutcome(summary = SyncSummary(failed = listOf(key.name)))
    }

    /**
     * Whether this ends the whole run rather than failing one file. A stopped run is not a file that failed: caught as
     * one, a cancellation would fill the log with a line per file still in flight and hide whatever actually ended the
     * run. The other three say something about every file after this one as well.
     */
    private val Exception.endsTheRun
        get() = this is CancellationException || this is SyncRunEndingException

    /**
     * Overwrites the local file, so it is read, decided about and only then written: the plan was made from hashes
     * taken when the run listed the library, and a long run gives the user plenty of time to save an edit to a song
     * that is still waiting to come down. The same goes for a file that was not there at all when the run listed the
     * library and is now: nothing planned for this name knew about it, so it is never written over. Checked before the
     * request, so that a file already in step is never transferred, and again after it, together with the write under
     * [libraryFileLock], so that a save lands either before that check, which then sees it, or after the write.
     */
    private suspend fun download(pass: SyncPass, operation: SyncOperation.Download): OperationOutcome {
        val key = operation.key
        val index = pass.index
        // The two sides may already hold the same bytes - two devices given the same file, or a library that was
        // copied across by hand before sync was set up. Nothing has to travel for that, only the index.
        val local = libraryFileLocalSource.readLibraryFile(key.kind, key.name)
        if (local != null && isSameContent(pass, local, pass.remoteFiles[key]?.contentHash)) {
            return OperationOutcome(entries = mapOf(key to SyncIndexEntry(localContentHash(local), operation.revision)))
        }
        if (local != null && localContentHash(local) != index[key]?.localHash) {
            // Not the file the last run saw. With an index entry it was edited since the listing; with none it was not
            // there at all when the run listed the library, so it appeared since - a conflict copy written earlier in
            // this pass, or a song the user made while the run was going. Either way it has changed on both sides, and
            // it is resolved as that rather than written over.
            return resolve(pass, SyncOperation.Resolve(key, operation.revision))
        }
        val downloaded = downloadWithinLimit(pass, key)
        // The request can take minutes under rate limiting, and the user is free to save this very file meanwhile:
        // decided again on what is there now. The conflict it may turn out to be is resolved with the lock let go,
        // since resolving it is more requests.
        val changed = libraryFileLock.withLock {
            val current = libraryFileLocalSource.readLibraryFile(key.kind, key.name)
            if (current != null && !current.contentEquals(local)) {
                current
            } else {
                libraryFileLocalSource.writeLibraryFile(key.kind, key.name, downloaded.bytes)
                pass.run.onLocalFileChanged(key)
                null
            }
        }
        if (changed != null) {
            return conflictResolver.resolveWith(
                pass = pass,
                key = key,
                revision = downloaded.revision,
                localBytes = changed,
                remote = downloaded.bytes,
                indexEntry = index[key],
            )
        }
        // The revision of what was fetched rather than the listing's: another device may have written the file again in
        // between, and an index naming the older revision for the newer content would make this device's next edit of
        // it a conflict with its own previous version.
        return OperationOutcome(
            entries = mapOf(key to SyncIndexEntry(localContentHash(downloaded.bytes), downloaded.revision)),
            summary = SyncSummary(downloaded = 1),
        )
    }

    /**
     * Read, decided about and only then deleted, for the same reason as [download]: the plan saw the file unchanged,
     * which says nothing about the minutes the run has taken since. An edit made in between beats the deletion, the
     * same rule [SyncPlanner] applies, and puts the file back on the remote. The check and the deletion are one step
     * under [libraryFileLock], so that a save cannot land between them.
     */
    private suspend fun deleteLocally(pass: SyncPass, operation: SyncOperation.DeleteLocal): OperationOutcome {
        val key = operation.key
        val outcome = libraryFileLock.withLock {
            val local = libraryFileLocalSource.readLibraryFile(key.kind, key.name)
            when {
                local == null -> OperationOutcome(removals = setOf(key))
                localContentHash(local) != pass.index[key]?.localHash -> null
                else -> {
                    libraryFileLocalSource.deleteLibraryFile(key.kind, key.name)
                    pass.run.onLocalFileChanged(key)
                    OperationOutcome(removals = setOf(key), summary = SyncSummary(deletedLocally = 1))
                }
            }
        }
        return outcome ?: upload(pass, SyncOperation.Upload(key, expectedRevision = null))
    }

    private suspend fun upload(pass: SyncPass, operation: SyncOperation.Upload): OperationOutcome {
        val key = operation.key
        // Deleted between the listing and now, which the next run will see as a deletion and handle properly.
        val bytes = libraryFileLocalSource.readLibraryFile(key.kind, key.name) ?: return OperationOutcome()
        return when (val result = pass.run.provider.upload(key.kind, key.name, bytes, operation.expectedRevision)) {
            is RemoteWriteResult.Written -> OperationOutcome(
                entries = mapOf(key to SyncIndexEntry(localContentHash(bytes), result.revision)),
                summary = SyncSummary(uploaded = 1),
            )

            RemoteWriteResult.Conflict -> if (operation.expectedRevision == null && key in pass.caseCollisions) {
                // Refused because the service already holds this name in another spelling, which is the other local file.
                // Another pass would be refused the same way, so this is a file that could not be synced rather than a
                // conflict waiting to be resolved.
                logger.log("Could not sync \"${key.path}\": the service holds the same name in another case.")
                OperationOutcome(summary = SyncSummary(failed = listOf(key.name)))
            } else {
                // The remote file moved under the write, which asks for another pass over a fresh listing.
                OperationOutcome(isUnresolved = true)
            }
        }
    }

    /**
     * A file that changed on both sides. The local version keeps the name and goes up; the remote one comes down
     * next to it under a free name and goes back up under that name, so that both devices end with both versions
     * and the same two names. Nothing is merged, and nothing is thrown away - except where the file is a setlist and
     * the only difference is the day it names, which [ConflictResolver.resolveWith] settles by taking the remote version.
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
    private suspend fun resolve(pass: SyncPass, operation: SyncOperation.Resolve): OperationOutcome {
        val key = operation.key
        val local = libraryFileLocalSource.readLibraryFile(key.kind, key.name) ?: return OperationOutcome()
        // The two sides changed to the same thing, which is not a conflict at all - the same edit made twice.
        if (isSameContent(pass, local, pass.remoteFiles[key]?.contentHash)) {
            return OperationOutcome(entries = mapOf(key to SyncIndexEntry(localContentHash(local), operation.revision)))
        }
        val remote = downloadWithinLimit(pass, key)
        // Uploaded over the revision it was compared with, so that a version that moved on again since is a conflict.
        return conflictResolver.resolveWith(
            pass = pass,
            key = key,
            revision = remote.revision,
            localBytes = local,
            remote = remote.bytes,
            indexEntry = pass.index[key],
        )
    }

    private fun isSameContent(pass: SyncPass, local: ByteArray, remoteContentHash: String?) =
        remoteContentHash != null && pass.run.provider.contentHashOf(local) == remoteContentHash

    /**
     * A song is a few kilobytes of text, and a download is held in memory whole. Refused here rather than left out
     * of the listing: a file the planner cannot see on the remote is a file it takes for deleted there, and a
     * large one that is already in the library would be deleted locally for it. Thrown, it is one file's failure
     * like any other - logged, the index left alone, tried again by the next run.
     */
    private suspend fun downloadWithinLimit(pass: SyncPass, key: SyncKey): RemoteDocument {
        val size = pass.remoteFiles[key]?.size ?: 0
        if (size > MAXIMUM_FILE_SIZE) throw RemoteFileTooLargeException(size)
        return pass.run.provider.download(key.kind, key.name)
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
            logger.recovering(
                // Tidying up is not worth a run: the entry is dropped either way, and the file stays where it is.
                describe = { "Could not remove the local copy of \"${key.path}\": ${it.message}" },
                fallback = {},
            ) {
                libraryFileLock.withLock {
                    val local = libraryFileLocalSource.readLibraryFile(key.kind, key.name)
                    if (isStillRemote && local != null && localContentHash(local) == entry.localHash) {
                        libraryFileLocalSource.deleteLibraryFile(key.kind, key.name)
                    }
                }
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

    sealed interface Result {

        data class Completed(
            val summary: SyncSummary,
            val index: SyncIndexDocument,
        ) : Result

        /**
         * Nothing moved: the plan would have deleted [count] of the [total] files the index knows from the side
         * [direction] names, and asks first.
         */
        data class DeletionsNeedConfirmation(
            val count: Int,
            val total: Int,
            val direction: SyncDeletionDirection,
        ) : Result
    }

    /** What one operation changed, merged into the pass by whoever finishes first. */
    internal data class OperationOutcome(
        val entries: Map<SyncKey, SyncIndexEntry> = emptyMap(),
        val removals: Set<SyncKey> = emptySet(),
        val summary: SyncSummary = SyncSummary(),
        val isUnresolved: Boolean = false,
    )

    private class RemoteFileTooLargeException(size: Long) :
        Exception("The remote file is $size bytes, which is more than the $MAXIMUM_FILE_SIZE a run downloads.")

    private data class PassOutcome(
        val summary: SyncSummary,
        val index: Map<SyncKey, SyncIndexEntry>,
        val unresolved: Set<SyncKey>,
    )

    private companion object {
        const val MAXIMUM_PASSES = 2

        /** How many library files are read and hashed at once while the run is preparing. */
        const val READ_BATCH_SIZE = 64

        /**
         * The largest file a run reads or downloads: what an import accepts, since a larger one is not a song and no
         * other device would take it either. Generous for ChordPro text, and small enough that [CONCURRENT_TRANSFERS]
         * of them in memory at once do not trouble a phone.
         */
        const val MAXIMUM_FILE_SIZE = ImportLimits.MAX_TEXT_FILE_SIZE

        /**
         * Chosen for the round trip rather than for the CPU: the transfers are small text files and almost all of
         * the time is spent waiting on the network, so this is about how many answers can be in flight before the
         * service starts rate limiting instead.
         */
        const val CONCURRENT_TRANSFERS = 6
    }
}
