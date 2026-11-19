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

import com.pandulapeter.campfire.data.model.library.SyncCollection
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
 * - An edit beats a deletion. A file changed on one side and deleted on the other comes back, because a document that
 *   reappears is an annoyance while one that silently vanishes is lost work.
 *
 * The planner knows no moves, so a rename is a deletion of one name and a new file under another. Where the index
 * already knows the new name holding the same bytes (a run cut off between uploading a rename and deleting its old
 * name, or a document held twice), the renamed file reads as that file unchanged, and a deletion of it elsewhere would win
 * while the rename's other half deletes the old name from the folder. So a pass never deletes one version on both
 * sides: a local deletion of bytes the same plan deletes remotely under another name puts the file back instead.
 *
 * Content files (a [SyncCollection.Role.CONTENT_FILE] collection: in Campfire, pictures and attachments) follow the
 * documents that name them ([SyncReferences]), by one more rule, the twin of the edit beating the deletion: **a
 * reference beats a deletion** ([withImagesKept]).
 */
internal object SyncPlanner {

    /** The operations of [planned], for a caller that needs nothing else of the plan. */
    fun plan(
        local: List<LocalFileState>,
        remote: List<RemoteFileState>,
        index: Map<SyncKey, SyncIndexEntry>,
        imageReferences: Map<SyncKey, Set<String>>? = emptyMap(),
        referencing: Set<SyncCollection>,
    ): List<SyncOperation> = planned(local, remote, index, imageReferences, referencing).operations

    /**
     * @param imageReferences The content files each referencing document of this device names, null where some such
     *   document could not be read.
     * @param referencing The collections whose documents name content files, whether or not this device holds any.
     */
    fun planned(
        local: List<LocalFileState>,
        remote: List<RemoteFileState>,
        index: Map<SyncKey, SyncIndexEntry>,
        imageReferences: Map<SyncKey, Set<String>>?,
        referencing: Set<SyncCollection>,
    ): SyncPlan {
        val localByKey = local.associateBy { it.key }
        val remoteByKey = remote.associateBy { it.key }
        val kept = (localByKey.keys + remoteByKey.keys + index.keys).mapNotNull { key ->
            operationFor(key = key, local = localByKey[key], remote = remoteByKey[key], indexEntry = index[key])
        }.withImagesKept(imageReferences, referencing)
        val operations = kept.operations
        // Renames only: a content file has none, and its index hash is its size, which two such files may share.
        val deletedRemotely = operations.filterIsInstance<SyncOperation.DeleteRemote>()
            .filter { it.key.kind.role != SyncCollection.Role.CONTENT_FILE }
            .mapNotNull { operation -> index[operation.key]?.let { operation.key.kind to it.localHash } }
            .toSet()
        if (deletedRemotely.isEmpty()) return kept
        return kept.copy(
            operations = operations.map { operation ->
                val hash = localByKey[operation.key]?.hash
                if (operation is SyncOperation.DeleteLocal && (operation.key.kind to hash) in deletedRemotely) {
                    SyncOperation.Upload(operation.key, expectedRevision = null)
                } else {
                    operation
                }
            },
        )
    }

    /**
     * A device deletes a content file once no document of its own names it, and that deletion reaches every other
     * device, where a document may have just started naming it. So a file a document here names is never deleted on
     * either side: one deleted over there goes back up, one deleted here comes back down. The device holding the
     * document is the one that puts the file back, which is why every device keeping its own documents' files is enough
     * for none to be lost.
     *
     * What this device's documents name is known only for the ones it holds, and which ones it holds is what a pass that
     * brings one down or deletes one on either side is changing: one coming down may name a file this plan would delete,
     * and one deleted here may still be kept by an edit made during the run. So such a pass deletes no content file at
     * all, on either side, and neither does one where a referencing document could not be read ([imageReferences]
     * null): the deletions wait for a run that knows every document it will hold. A document this pass deletes here does
     * not keep its files either, which would only send them back up for the next run to delete again. A plan whose
     * deletions wait only for the documents to settle says so ([SyncPlan.holdsDeletionsBack]); one waiting for an
     * unreadable document does not, since the next run would wait as well.
     */
    private fun List<SyncOperation>.withImagesKept(imageReferences: Map<SyncKey, Set<String>>?, referencing: Set<SyncCollection>): SyncPlan {
        val referencingOperations = filter { it.key.kind in referencing }
        // A document deleted from the folder counts too: another device's edit may beat that deletion, and bring it back
        // naming a file deleted beside it (a device of a version that knows no content files, which never puts one back).
        val isReferencingSetChanging = referencingOperations.any { it !is SyncOperation.Upload && it !is SyncOperation.Forget }
        val leaving = referencingOperations.filterIsInstance<SyncOperation.DeleteLocal>().mapTo(mutableSetOf()) { it.key }
        val kept = imageReferences?.filterKeys { it !in leaving }?.values?.flatten()?.toSet()
        val mayDelete = kept != null && !isReferencingSetChanging
        var holdsDeletionsBack = false
        val operations = mapNotNull { operation ->
            val key = operation.key
            val isKept = kept?.contains(key.name) == true
            when {
                key.kind.role != SyncCollection.Role.CONTENT_FILE -> operation
                operation is SyncOperation.DeleteLocal -> when {
                    isKept -> SyncOperation.Upload(key, expectedRevision = null)
                    mayDelete -> operation
                    else -> null.also { holdsDeletionsBack = kept != null }
                }
                operation is SyncOperation.DeleteRemote -> when {
                    isKept -> SyncOperation.Download(key, operation.revision)
                    mayDelete -> operation
                    else -> null.also { holdsDeletionsBack = kept != null }
                }
                else -> operation
            }
        }
        return SyncPlan(operations = operations, holdsDeletionsBack = holdsDeletionsBack)
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
