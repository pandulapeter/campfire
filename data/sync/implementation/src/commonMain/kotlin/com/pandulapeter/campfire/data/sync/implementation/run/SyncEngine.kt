/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.sync.implementation.run

import com.pandulapeter.campfire.data.sync.implementation.index.SyncIndexDocument
import com.pandulapeter.campfire.data.sync.implementation.SyncIndexEntry
import com.pandulapeter.campfire.data.sync.implementation.SyncKey
import com.pandulapeter.campfire.data.sync.implementation.SyncOperation
import com.pandulapeter.campfire.data.model.library.SyncCollection
import com.pandulapeter.campfire.data.model.library.SyncCollections
import com.pandulapeter.campfire.data.model.Logger
import com.pandulapeter.campfire.data.model.sync.SyncDeletionDirection
import com.pandulapeter.campfire.data.model.sync.SyncDeletionPolicy
import com.pandulapeter.campfire.data.model.sync.SyncProgress
import com.pandulapeter.campfire.data.model.sync.SyncSummary
import com.pandulapeter.campfire.data.source.local.api.LibraryFileLock
import com.pandulapeter.campfire.data.source.local.api.LibraryFileLocalSource
import com.pandulapeter.campfire.data.source.local.api.SetlistComparison
import com.pandulapeter.campfire.data.source.remote.api.sync.SyncFolder
import com.pandulapeter.campfire.data.source.remote.api.sync.SyncRunEndingException
import com.pandulapeter.campfire.data.source.remote.api.sync.model.RemoteDeletion
import com.pandulapeter.campfire.data.source.remote.api.sync.model.RemoteFile
import com.pandulapeter.campfire.data.source.remote.api.sync.model.RemoteSharedFile
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
    plantedContentHash: suspend (SyncKey) -> String?,
    /** Where the failures of single files are written. */
    private val logger: Logger,
    /** What the library is made of, which is how the keys of the index are read back. */
    val collections: SyncCollections,
    /**
     * Above this many bytes, a library file is moved alone rather than beside others: every file is held in memory whole
     * while it moves, several at once ([CONCURRENT_TRANSFERS]). Below it, that many at once are what a phone has always
     * spared a run.
     */
    private val largeFileThreshold: Long,
    /** Runs the deletion of pictures here exclusively of the app's writes of a picture and its song. */
    imageWritesGate: suspend (suspend (held: Set<String>) -> Unit) -> Unit = { it(emptySet()) },
) {

    private val conflictResolver = ConflictResolver(libraryFileLocalSource, libraryFileLock, setlistComparison, plantedContentHash, logger)
    private val imageTransfers = ImageTransfers(libraryFileLocalSource, libraryFileLock, logger, imageWritesGate)
    private val lostFileRestorer = LostFileRestorer(libraryFileLocalSource, logger)
    private val documentTransfers = DocumentTransfers(libraryFileLocalSource, libraryFileLock, conflictResolver, logger)

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
        var index = document.takeIf { it.accountId == accountId }?.toIndex(collections).orEmpty()
        var syncedPreferences = document.syncedPreferencesBase().takeIf { document.accountId == accountId }
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
        var cursor: String? = null
        var documentRevisions = emptyMap<String, String>()
        var sharedFiles = emptyList<RemoteSharedFile>()
        var asksForFollowUp = false
        val startIndex = index
        var lastListed = emptyList<RemoteFile>()
        var lastLocalListing: LocalListing? = null
        val runDeletions = RunDeletions()
        while (pass < MAXIMUM_PASSES) {
            // Listing both sides is the part of a run with nothing to show for it yet, so the progress reported
            // here has no total and the indicator spins rather than sitting at zero.
            onProgress(SyncProgress())
            val listing = provider.list()
            val listed = listing.files
            cursor = listing.cursor
            documentRevisions = listing.documentRevisions
            sharedFiles = listing.sharedFiles
            index = withoutForeignEntries(index = index, listed = listed, libraryFileLocalSource = libraryFileLocalSource, libraryFileLock = libraryFileLock, logger = logger)
            val localListing = readLocalListing(
                libraryFileLocalSource = libraryFileLocalSource,
                logger = logger,
                batchSize = READ_BATCH_SIZE,
                largeFileThreshold = largeFileThreshold,
            )
            lastListed = listed
            lastLocalListing = localListing
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
            val planned = SyncPlanner.planned(local = local, remote = remote, index = planningIndex, imageReferences = localListing.imageReferences)
            val plan = planned.operations
            if (planned.holdsDeletionsBack) asksForFollowUp = true
            logger.log("Sync: pass ${pass + 1}, ${remote.size} files in the folder and ${local.size} here: ${plan.describedForLog()}")
            // Which is what most runs find, so nothing below this costs anything on an ordinary launch.
            if (plan.isEmpty()) {
                unresolved = emptySet()
                break
            }
            runDeletions.check(
                plan = plan,
                planningIndex = planningIndex,
                isLocalListingEmpty = local.none { it.key.kind.isDocument },
                policy = deletionPolicy,
            )?.let { question ->
                logger.log("Sync: stopped to ask before ${question.count} of ${question.total} files are deleted (${question.direction})")
                return question
            }
            val outcome = apply(
                pass = SyncPass(
                    run = run,
                    index = index,
                    remoteFiles = remote.associateBy { it.key },
                    caseCollisions = caseCollisions,
                    syncedPreferences = syncedPreferences,
                    largeFiles = localListing.largeFiles + remote.filter { it.size > largeFileThreshold }.map { it.key },
                    imageReferences = localListing.imageReferences,
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
        // The next run downloads what came back, whose integrity check is what guards against a wrong revision.
        if (lastLocalListing != null && lostFileRestorer.restoreMissing(provider, lastLocalListing, lastListed, before = startIndex, after = index)) {
            asksForFollowUp = true
        }

        return Result.Completed(
            summary = summary,
            cursor = cursor,
            documentRevisions = documentRevisions,
            sharedFiles = sharedFiles,
            asksForFollowUp = asksForFollowUp,
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
     * Every operation is at least one request of its own, and they used to be run one after another - which made a
     * first sync of a few hundred songs a few hundred round trips end to end, and the slowest thing the app does by
     * a wide margin. Files do not depend on each other, so they go at once, [CONCURRENT_TRANSFERS] at a time: enough
     * to hide the latency, few enough that the service answers with files rather than with rate limiting.
     *
     * The ordering that does matter is kept between the groups: outgoing pictures, incoming files, other uploads, deletions.
     * A song uploaded before its pictures, by a run killed in between, names files the folder lacks, and a conflict uploads
     * this device's version of a song too, so the pictures go first of all, needing nothing else on disk; a download has
     * to be on disk before anything that reads the library acts on it, and a deletion that ran before a download would
     * undo it. Remote deletions go to the provider together ([deleteRemotely]), and a
     * picture's here as one batch ([ImageTransfers.deleteLocally]), neither spread over the permits. A large file moves
     * alone besides ([largeFileThreshold]): a few attachments at 50 MB each, held whole, are more than a phone's heap.
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
        val largeFilePermit = Semaphore(1)
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
            group.filterIsInstance<SyncOperation.DeleteRemote>().takeIf { it.isNotEmpty() }
                ?.let { deleteRemotely(pass, it) }?.forEach { (operation, outcome) -> record(operation, outcome) }
            val pictureDeletions = group.filter { it is SyncOperation.DeleteLocal && it.isImage }
            imageTransfers.deleteLocally(pass, pictureDeletions).forEach { (operation, outcome) -> record(operation, outcome) }
            group.filterNot { it is SyncOperation.DeleteRemote || it in pictureDeletions }.map { operation ->
                // A large file takes its own permit first, so that one waiting for it holds none of the others' meanwhile.
                val gate = if (operation.key in pass.largeFiles) largeFilePermit else null
                async { record(operation, gate.withOptionalPermit { permits.withPermit { runOperation(pass, operation) } }) }
            }.awaitAll()
        }
        PassOutcome(summary = summary, index = updated, unresolved = unresolved)
    }

    private suspend fun runOperation(pass: SyncPass, operation: SyncOperation): OperationOutcome = try {
        when (operation) {
            // A picture's name is its content, so both sides holding one hold the same picture or one of them is damaged:
            // there is never a conflict to keep two versions of.
            is SyncOperation.Download -> if (operation.isImage) imageTransfers.bringDown(pass, operation.key, operation.revision) else documentTransfers.download(pass, operation)
            is SyncOperation.Upload -> if (operation.isImage) imageTransfers.sendUp(pass, operation) else documentTransfers.upload(pass, operation)
            is SyncOperation.Resolve -> if (operation.isImage) {
                imageTransfers.bringDown(pass, operation.key, operation.revision)
            } else {
                documentTransfers.resolve(pass, operation, isFirstMeeting = operation.key !in pass.index)
            }
            is SyncOperation.DeleteLocal -> documentTransfers.deleteLocally(pass, operation)

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

    private val SyncOperation.isImage get() = key.kind.role == SyncCollection.Role.CONTENT_FILE
    private val SyncOperation.order
        get() = when (this) {
            is SyncOperation.Upload -> if (isImage) 0 else 3
            is SyncOperation.Resolve -> 1
            is SyncOperation.Download -> 2
            is SyncOperation.DeleteLocal -> 4
            is SyncOperation.DeleteRemote -> 5
            is SyncOperation.Forget -> 6
        }

    sealed interface Result {

        /**
         * @param cursor The cursor of the last listing, see `RemoteListing.cursor`: every change made after it is either
         *   this run's, and so in [index], or one the run did not see.
         * @param documentRevisions The folder-level documents' revisions, as the run's last listing named them.
         * @param sharedFiles The shared copies, as the run's last listing named them, for the shared-copy step.
         * @param asksForFollowUp Whether the run left something for one more run to finish, which nothing else would
         *   start: pictures no song names any more, kept while the songs were changing ([SyncPlan.holdsDeletionsBack]),
         *   or files a song names brought back into the folder from its history ([LostFileRestorer]).
         */
        data class Completed(
            val summary: SyncSummary,
            val cursor: String?,
            val documentRevisions: Map<String, String>,
            val sharedFiles: List<RemoteSharedFile>,
            val asksForFollowUp: Boolean,
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
         * Chosen for the round trip rather than for the CPU: the transfers are small text files and almost all of
         * the time is spent waiting on the network, so this is about how many answers can be in flight before the
         * service starts rate limiting instead.
         */
        const val CONCURRENT_TRANSFERS = 6
    }
}
