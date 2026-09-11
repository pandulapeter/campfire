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

import kotlinx.serialization.Serializable

/**
 * The on-disk shape of `sync-index.json`: what the last successful run saw, which is the only reason the next one
 * can tell a file that was deleted from one that has not arrived yet.
 *
 * [accountId] is part of it because an index describes one remote folder. Connecting a different account leaves it
 * meaningless, and acting on it would read that account's absent files as deletions of this one's songs.
 */
@Serializable
internal data class SyncIndexDocument(
    val providerId: String = "",
    val accountId: String = "",
    /** Milliseconds since the epoch, 0 when no run has finished yet. */
    val lastSyncedAt: Long = 0,
    /**
     * Written true when a run starts and false when one finishes, so that a run the app never came back from -
     * killed, swiped away, suspended by iOS - is still recognisable as interrupted the next time it starts.
     */
    val isRunInProgress: Boolean = false,
    /** Keyed by `songs/Artist - Title.cho`, see [SyncKey.path]. */
    val entries: Map<String, Entry> = emptyMap(),
) {

    @Serializable
    data class Entry(
        val localHash: String = "",
        val remoteRevision: String = "",
    )

    fun toIndex(): Map<SyncKey, SyncIndexEntry> = entries.mapNotNull { (path, entry) ->
        SyncKey.fromPath(path)?.let { it to SyncIndexEntry(localHash = entry.localHash, remoteRevision = entry.remoteRevision) }
    }.toMap()

    companion object {

        fun of(
            providerId: String,
            accountId: String,
            lastSyncedAt: Long,
            index: Map<SyncKey, SyncIndexEntry>,
        ) = SyncIndexDocument(
            providerId = providerId,
            accountId = accountId,
            lastSyncedAt = lastSyncedAt,
            isRunInProgress = false,
            entries = index.entries.associate { (key, entry) ->
                key.path to Entry(localHash = entry.localHash, remoteRevision = entry.remoteRevision)
            },
        )
    }
}
