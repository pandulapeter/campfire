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

import com.pandulapeter.campfire.data.model.Logger
import com.pandulapeter.campfire.data.model.sync.SyncSummary
import com.pandulapeter.campfire.data.source.local.api.LibraryFileLocalSource
import com.pandulapeter.campfire.data.source.local.api.LibraryFileLock
import com.pandulapeter.campfire.data.source.remote.api.sync.model.RemoteWriteResult
import com.pandulapeter.campfire.data.sync.implementation.SyncIndexEntry
import com.pandulapeter.campfire.data.sync.implementation.SyncOperation
import com.pandulapeter.campfire.data.sync.implementation.run.SyncEngine.OperationOutcome
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * How a document moves: every change to the local file is decided again just before it is made, under
 * [libraryFileLock], and a file changed on both sides goes to the [conflictResolver]. Content files move by
 * [ImageTransfers].
 */
internal class DocumentTransfers(
    private val libraryFileLocalSource: LibraryFileLocalSource,
    private val libraryFileLock: LibraryFileLock,
    private val conflictResolver: ConflictResolver,
    private val logger: Logger,
) {

    /**
     * Overwrites the local file, so it is read, decided about and only then written: the plan was made from hashes
     * taken when the run listed the library, and a long run gives the user plenty of time to save an edit to a
     * document that is still waiting to come down. The same goes for a file that was not there at all when the run
     * listed the library and is now: nothing planned for this name knew about it, so it is never written over. Checked
     * before the request, so that a file already in step is never transferred, and again after it, together with the
     * write under [libraryFileLock], so that a save lands either before that check, which then sees it, or after the
     * write.
     */
    suspend fun download(pass: SyncPass, operation: SyncOperation.Download): OperationOutcome {
        val key = operation.key
        val index = pass.index
        // The two sides may already hold the same bytes - two devices given the same file, or a library that was
        // copied across by hand before sync was set up. Nothing has to travel for that, only the index.
        val local = libraryFileLocalSource.readLibraryFile(key.kind, key.name)
        if (local != null && isSameContent(pass, local, pass.remoteFiles[key]?.contentHash)) {
            return OperationOutcome(entries = mapOf(key to SyncIndexEntry(localHashOf(key, local), operation.revision)))
        }
        if (local != null && localHashOf(key, local) != index[key]?.localHash) {
            // Not the file the last run saw. With an index entry it was edited since the listing; with none it was not
            // there at all when the run listed the library, so it appeared since - a conflict copy written earlier in
            // this pass, or a document the user made while the run was going. Either way it has changed on both sides, and
            // it is resolved as that rather than written over.
            return resolve(pass, SyncOperation.Resolve(key, operation.revision), isFirstMeeting = false)
        }
        val downloaded = pass.downloadWithinLimit(key)
        // The request can take minutes under rate limiting, and the user is free to save this very file meanwhile, under
        // any spelling: decided again on what is there now, and the conflict it may be is resolved without the lock.
        val changed = libraryFileLock.withLock {
            val held = libraryFileLocalSource.spellingHeldOf(key)
            val current = libraryFileLocalSource.readLibraryFile(held.kind, held.name)
            if (current != null && !current.contentEquals(local)) {
                current
            } else {
                // A write once started finishes, and its notice with it: a Stop landing while the platform writes would
                // otherwise leave the file changed on disk, unknown to the repositories, and the lock let go before the
                // write has settled (the web resumes a cancelled call while its write is still in flight).
                withContext(NonCancellable) {
                    libraryFileLocalSource.writeLibraryFile(held.kind, held.name, downloaded.bytes)
                    pass.run.onLocalFileChanged(held)
                }
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
                isFirstMeeting = false,
            )
        }
        // The revision of what was fetched rather than the listing's: another device may have written the file again in
        // between, and an index naming the older revision for the newer content would make this device's next edit of
        // it a conflict with its own previous version.
        return OperationOutcome(
            entries = mapOf(key to SyncIndexEntry(localHashOf(key, downloaded.bytes), downloaded.revision)),
            summary = SyncSummary(downloaded = 1),
        )
    }

    /**
     * Read, decided about and only then deleted, for the same reason as [download]: the plan saw the file unchanged,
     * which says nothing about the minutes the run has taken since. An edit made in between beats the deletion, the
     * same rule [SyncPlanner] applies, and puts the file back on the remote. The check and the deletion are one step
     * under [libraryFileLock], so that a save cannot land between them.
     */
    suspend fun deleteLocally(pass: SyncPass, operation: SyncOperation.DeleteLocal): OperationOutcome {
        val key = operation.key
        val outcome = libraryFileLock.withLock {
            val local = libraryFileLocalSource.readLibraryFile(key.kind, key.name)
            when {
                local == null -> OperationOutcome(removals = setOf(key))
                localHashOf(key, local) != pass.index[key]?.localHash -> null
                else -> {
                    // Finished together once started, as in [download].
                    withContext(NonCancellable) {
                        libraryFileLocalSource.deleteLibraryFile(key.kind, key.name)
                        pass.run.onLocalFileChanged(key)
                    }
                    OperationOutcome(removals = setOf(key), summary = SyncSummary(deletedLocally = 1))
                }
            }
        }
        return outcome ?: upload(pass, SyncOperation.Upload(key, expectedRevision = null))
    }

    suspend fun upload(pass: SyncPass, operation: SyncOperation.Upload): OperationOutcome {
        val key = operation.key
        // Deleted between the listing and now, which the next run will see as a deletion and handle properly.
        val bytes = libraryFileLocalSource.readLibraryFile(key.kind, key.name) ?: return OperationOutcome()
        return when (val result = pass.run.provider.upload(key.kind, key.name, bytes, operation.expectedRevision)) {
            is RemoteWriteResult.Written -> OperationOutcome(
                entries = mapOf(key to SyncIndexEntry(localHashOf(key, bytes), result.revision)),
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
     * and the same two names. A file both sides held when the pass listed them that the index has never seen
     * ([isFirstMeeting]) goes the other way round. Nothing is merged, and nothing is thrown away - except where the
     * file's collection has a [ConflictPolicy] and the only difference is the volatile part it ignores (a setlist's
     * day, in Campfire), which [ConflictResolver.resolveWith] settles by taking the remote version.
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
    suspend fun resolve(pass: SyncPass, operation: SyncOperation.Resolve, isFirstMeeting: Boolean): OperationOutcome {
        val key = operation.key
        val local = libraryFileLocalSource.readLibraryFile(key.kind, key.name) ?: return OperationOutcome()
        // The two sides changed to the same thing, which is not a conflict at all - the same edit made twice.
        if (isSameContent(pass, local, pass.remoteFiles[key]?.contentHash)) {
            return OperationOutcome(entries = mapOf(key to SyncIndexEntry(localHashOf(key, local), operation.revision)))
        }
        val remote = pass.downloadWithinLimit(key)
        // Uploaded over the revision it was compared with, so that a version that moved on again since is a conflict.
        return conflictResolver.resolveWith(
            pass = pass,
            key = key,
            revision = remote.revision,
            localBytes = local,
            remote = remote.bytes,
            indexEntry = pass.index[key],
            isFirstMeeting = isFirstMeeting,
        )
    }

    private fun isSameContent(pass: SyncPass, local: ByteArray, remoteContentHash: String?) =
        remoteContentHash != null && pass.run.provider.contentHashOf(local) == remoteContentHash
}
