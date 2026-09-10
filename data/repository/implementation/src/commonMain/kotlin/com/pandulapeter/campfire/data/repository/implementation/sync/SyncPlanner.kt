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

import com.pandulapeter.campfire.data.model.domain.LibraryFileKind

/**
 * One file, on either side of the sync. Songs and setlists live in separate folders, so a name alone would not
 * identify anything.
 */
internal data class SyncKey(
    val kind: LibraryFileKind,
    val name: String
) {

    /** How the key is written in `sync-index.json`, and the shape of a remote path: `songs/Artist - Title.cho`. */
    val path get() = "${kind.id}/$name"

    companion object {

        fun fromPath(path: String): SyncKey? {
            val kind = LibraryFileKind.fromId(path.substringBefore('/')) ?: return null
            return path.substringAfter('/', "").takeIf { it.isNotEmpty() }?.let { SyncKey(kind = kind, name = it) }
        }
    }
}

/** A local file, identified by the hash of its content - never by its modification time, see `localContentHash`. */
internal data class LocalFileState(
    val key: SyncKey,
    val hash: String
)

/** A remote file, identified by whatever the provider calls a revision. Opaque here: only ever compared for equality. */
internal data class RemoteFileState(
    val key: SyncKey,
    val revision: String,
    val contentHash: String?
)

/** What the last successful run left behind for one file: the pair that was in step at that moment. */
internal data class SyncIndexEntry(
    val localHash: String,
    val remoteRevision: String
)

/**
 * One thing the sync run has to do. Deliberately data rather than behaviour: this is the part of sync that is worth
 * testing, and it is only testable while it stays a pure function of three lists.
 */
internal sealed interface SyncOperation {

    val key: SyncKey

    /** The remote version is newer and comes down under its own name. */
    data class Download(override val key: SyncKey, val revision: String) : SyncOperation

    /** The local version is newer and goes up. [expectedRevision] is null when the file is new to the remote. */
    data class Upload(override val key: SyncKey, val expectedRevision: String?) : SyncOperation

    data class DeleteLocal(override val key: SyncKey) : SyncOperation

    data class DeleteRemote(override val key: SyncKey, val revision: String) : SyncOperation

    /**
     * Changed on both sides. Nothing is merged and nothing is thrown away: the local version keeps the name, and the
     * remote one is written next to it under a free one, exactly as an import that collides would be.
     */
    data class Resolve(override val key: SyncKey, val revision: String) : SyncOperation

    /** Gone from both sides, so the index entry goes too. */
    data class Forget(override val key: SyncKey) : SyncOperation
}

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
 */
internal object SyncPlanner {

    fun plan(
        local: List<LocalFileState>,
        remote: List<RemoteFileState>,
        index: Map<SyncKey, SyncIndexEntry>
    ): List<SyncOperation> {
        val localByKey = local.associateBy { it.key }
        val remoteByKey = remote.associateBy { it.key }
        return (localByKey.keys + remoteByKey.keys + index.keys).mapNotNull { key ->
            operationFor(key = key, local = localByKey[key], remote = remoteByKey[key], indexEntry = index[key])
        }
    }

    private fun operationFor(
        key: SyncKey,
        local: LocalFileState?,
        remote: RemoteFileState?,
        indexEntry: SyncIndexEntry?
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
