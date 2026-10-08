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

import com.pandulapeter.campfire.data.model.domain.LibraryFileKind

/**
 * One file, on either side of the sync. Songs and setlists live in separate folders, so a name alone would not
 * identify anything.
 */
internal data class SyncKey(
    val kind: LibraryFileKind,
    val name: String,
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
    val hash: String,
)

/** A remote file, identified by whatever the provider calls a revision. Opaque here: only ever compared for equality. */
internal data class RemoteFileState(
    val key: SyncKey,
    val revision: String,
    val contentHash: String?,
    /** In bytes. Never part of any decision about what changed; only what keeps the engine from downloading a video. */
    val size: Long = 0,
)

/** What the last successful run left behind for one file: the pair that was in step at that moment. */
internal data class SyncIndexEntry(
    val localHash: String,
    val remoteRevision: String,
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
     * remote one is written next to it under a free one, exactly as an import that collides would be - except a
     * setlist whose versions differ only in the day each device gave it, which takes the remote one (see `SyncEngine`).
     */
    data class Resolve(override val key: SyncKey, val revision: String) : SyncOperation

    /** Gone from both sides, so the index entry goes too. */
    data class Forget(override val key: SyncKey) : SyncOperation
}
