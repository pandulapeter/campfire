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

import com.pandulapeter.campfire.data.model.domain.SyncAccount
import kotlinx.serialization.Serializable

/**
 * The on-disk shape of `sync-index.json`: what the last successful run saw, which is the only reason the next one
 * can tell a file that was deleted from one that has not arrived yet.
 *
 * [accountId] is part of it because an index describes one remote folder. Connecting a different account leaves it
 * meaningless, and acting on it would read that account's absent files as deletions of this one's songs.
 *
 * An index filed under the key an earlier version used is taken over rather than ignored, see [adoptedBy].
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

    /**
     * This document as [account]'s, if it is: an index written when the key was still the account's e-mail address
     * is filed under the new key instead of being ignored, which would cost the user every deletion made since the
     * run that wrote it. What says it is the same account is the address itself - the services keep those unique,
     * the index is removed on disconnect, and so the only index that can be found under this account's address is
     * the one the previous version wrote for it, and would have accepted on exactly the same evidence.
     *
     * The new key is on disk with the next write, after which this has nothing left to do. An index that belongs to
     * another account comes back unchanged, for the engine to disregard as before.
     */
    fun adoptedBy(account: SyncAccount) = if (accountId.isNotEmpty() && accountId == account.legacyIndexKey()) {
        copy(accountId = account.indexKey())
    } else {
        this
    }

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
