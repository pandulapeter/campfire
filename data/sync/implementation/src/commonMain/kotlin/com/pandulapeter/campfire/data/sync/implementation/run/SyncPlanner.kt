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

import com.pandulapeter.campfire.data.model.library.LibraryFileKind
import com.pandulapeter.campfire.data.sync.implementation.LocalFileState
import com.pandulapeter.campfire.data.sync.implementation.RemoteFileState
import com.pandulapeter.campfire.data.sync.implementation.SyncIndexEntry
import com.pandulapeter.campfire.data.sync.implementation.SyncKey
import com.pandulapeter.campfire.data.sync.implementation.SyncOperation

/**
 * Works out what one sync run has to do, from what is on each side and what the last run saw.
 *
 * The index is what makes a deletion tellable from a file that was never there: a file that is missing locally but
 * has an index entry was deleted here, while one with no entry is simply new over there. Without it, the only safe
 * reading of "missing" would be "not yet downloaded", and no deletion could ever propagate.
 *
 * Two rules run through all of it:
 * - Content decides, never a clock. The four platforms disagree about modification times and one of them reports
 *   none at all, so a hash is the only thing that can say whether a file has changed.
 * - An edit beats a deletion. A file changed on one side and deleted on the other comes back, because a song that
 *   reappears is an annoyance while a song that silently vanishes is lost work.
 *
 * The planner knows no moves, so a rename is a deletion of one name and a new file under another. Where the index
 * already knows the new name holding the same bytes (a run cut off between uploading a rename and deleting its old
 * name, or a song held twice), the renamed file reads as that file unchanged, and a deletion of it elsewhere would win
 * while the rename's other half deletes the old name from the folder. So a pass never deletes one version on both
 * sides: a local deletion of bytes the same plan deletes remotely under another name puts the file back instead.
 *
 * Pictures ([LibraryFileKind.FILE]) follow the songs that name them, by one more rule, the twin of the edit beating the
 * deletion: **a reference beats a deletion** ([withImagesKept]).
 */
internal object SyncPlanner {

    /**
     * @param imageReferences The pictures each song of this device names, null where some song could not be read.
     */
    fun plan(
        local: List<LocalFileState>,
        remote: List<RemoteFileState>,
        index: Map<SyncKey, SyncIndexEntry>,
        imageReferences: Map<SyncKey, Set<String>>? = emptyMap(),
    ): List<SyncOperation> {
        val localByKey = local.associateBy { it.key }
        val remoteByKey = remote.associateBy { it.key }
        val operations = (localByKey.keys + remoteByKey.keys + index.keys).mapNotNull { key ->
            operationFor(key = key, local = localByKey[key], remote = remoteByKey[key], indexEntry = index[key])
        }.withImagesKept(imageReferences)
        // Renames only: a picture has none, and its index hash is its size, which two pictures may share.
        val deletedRemotely = operations.filterIsInstance<SyncOperation.DeleteRemote>()
            .filter { it.key.kind != LibraryFileKind.FILE }
            .mapNotNull { operation -> index[operation.key]?.let { operation.key.kind to it.localHash } }
            .toSet()
        if (deletedRemotely.isEmpty()) return operations
        return operations.map { operation ->
            val hash = localByKey[operation.key]?.hash
            if (operation is SyncOperation.DeleteLocal && (operation.key.kind to hash) in deletedRemotely) {
                SyncOperation.Upload(operation.key, expectedRevision = null)
            } else {
                operation
            }
        }
    }

    /**
     * A device deletes a picture once no song of its own names it, and that deletion reaches every other device, where a
     * song may have just started naming it. So a picture a song here names is never deleted on either side: one deleted
     * over there goes back up, one deleted here comes back down. The device holding the song is the one that puts the
     * picture back, which is why every device keeping its own songs' pictures is enough for none to be lost.
     *
     * What this device's songs name is known only for the songs it holds, and which songs it holds is what a pass that
     * brings one down or deletes one here is changing: one coming down may name a picture this plan would delete, and
     * one deleted here may still be kept by an edit made during the run. So such a pass deletes no picture at all, on
     * either side, and neither does one where a song could not be read ([imageReferences] null): the deletions wait for
     * a run that knows every song it will hold. A song this pass deletes here does not keep its pictures either, which
     * would only send them back up for the next run to delete again.
     */
    private fun List<SyncOperation>.withImagesKept(imageReferences: Map<SyncKey, Set<String>>?): List<SyncOperation> {
        val songOperations = filter { it.key.kind == LibraryFileKind.SONG }
        val isSongSetChanging = songOperations.any { it is SyncOperation.Download || it is SyncOperation.Resolve || it is SyncOperation.DeleteLocal }
        val leaving = songOperations.filterIsInstance<SyncOperation.DeleteLocal>().mapTo(mutableSetOf()) { it.key }
        val kept = imageReferences?.filterKeys { it !in leaving }?.values?.flatten()?.toSet()
        val mayDelete = kept != null && !isSongSetChanging
        return mapNotNull { operation ->
            val key = operation.key
            val isKept = kept?.contains(key.name) == true
            when {
                key.kind != LibraryFileKind.FILE -> operation
                operation is SyncOperation.DeleteLocal -> when {
                    isKept -> SyncOperation.Upload(key, expectedRevision = null)
                    mayDelete -> operation
                    else -> null
                }
                operation is SyncOperation.DeleteRemote -> when {
                    isKept -> SyncOperation.Download(key, operation.revision)
                    mayDelete -> operation
                    else -> null
                }
                else -> operation
            }
        }
    }

    private fun operationFor(
        key: SyncKey,
        local: LocalFileState?,
        remote: RemoteFileState?,
        indexEntry: SyncIndexEntry?,
    ): SyncOperation? = when {
        local != null && remote != null -> {
            val hasChangedLocally = indexEntry == null || indexEntry.localHash != local.hash
            val hasChangedRemotely = indexEntry == null || indexEntry.remoteRevision != remote.revision
            when {
                // In step and already written down, which is what most files are on most runs.
                !hasChangedLocally && !hasChangedRemotely -> null
                hasChangedLocally && !hasChangedRemotely -> SyncOperation.Upload(key, remote.revision)
                !hasChangedLocally && hasChangedRemotely -> SyncOperation.Download(key, remote.revision)
                // Both moved. The engine still gets a chance to notice that they moved to the same content - two
                // devices that were given the same file - and turn this into a Record before anything is copied.
                else -> SyncOperation.Resolve(key, remote.revision)
            }
        }

        local != null -> when {
            // Never seen by a sync run: new here, and the remote has no opinion about it yet.
            indexEntry == null -> SyncOperation.Upload(key, expectedRevision = null)
            // Deleted over there, but edited here since the last run: the edit wins and puts the file back.
            indexEntry.localHash != local.hash -> SyncOperation.Upload(key, expectedRevision = null)
            else -> SyncOperation.DeleteLocal(key)
        }

        remote != null -> when {
            indexEntry == null -> SyncOperation.Download(key, remote.revision)
            // Deleted here, but changed over there since the last run: the edit wins, the same way round.
            indexEntry.remoteRevision != remote.revision -> SyncOperation.Download(key, remote.revision)
            else -> SyncOperation.DeleteRemote(key, indexEntry.remoteRevision)
        }

        indexEntry != null -> SyncOperation.Forget(key)
        else -> null
    }
}
