/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.model.domain

/** The cloud services the library can be kept in step with. One is connected at a time, see `SyncRepository`. */
enum class SyncProviderId(val id: String) {
    DROPBOX("dropbox");

    companion object {
        fun fromId(id: String) = entries.firstOrNull { it.id == id }
    }
}

/** Who the connected provider says the user is, shown so that it is obvious which account the library goes to. */
data class SyncAccount(
    val providerId: SyncProviderId,
    val displayName: String,
    val email: String?,
)

/**
 * Everything the UI needs to know about sync. [Connected] carries the outcome of the last run rather than a message
 * of its own, so that a failure stays on screen until something replaces it instead of flashing past in a snackbar.
 */
sealed interface SyncState {

    data object Disconnected : SyncState

    /** The browser has been opened and the authorization has not come back yet. */
    data class Connecting(val providerId: SyncProviderId) : SyncState

    data class Connected(
        val account: SyncAccount,
        /** Non-null while a run is going, and what the progress indicator is driven from. */
        val progress: SyncProgress?,
        /** Milliseconds since the epoch of the last run that *finished*, null until one has. */
        val lastSyncedAt: Long?,
        val lastOutcome: SyncOutcome?,
    ) : SyncState {

        val isSyncing get() = progress != null
    }
}

/**
 * How far along a run is. [total] is the number of files the plan turned out to need, which is only known once the
 * library and the remote folder have both been listed - so a run starts [isPreparing] with nothing to count yet.
 */
data class SyncProgress(
    val completed: Int = 0,
    val total: Int = 0,
) {

    val isPreparing get() = total == 0

    /** Null while preparing, so the indicator can spin rather than sit at zero. */
    val fraction: Float? get() = if (total == 0) null else (completed.toFloat() / total).coerceIn(0f, 1f)
}

sealed interface SyncOutcome {

    data class Success(val summary: SyncSummary) : SyncOutcome

    data class Failure(val reason: SyncFailureReason) : SyncOutcome

    /**
     * The run did not finish and did not fail either: the user stopped it, or the app was closed or suspended out
     * from under it. Told apart from a failure because there is nothing wrong to report and nothing to fix - what
     * was transferred stayed transferred, and the next run carries on from there.
     */
    data object Interrupted : SyncOutcome
}

/**
 * What one sync run did. [conflicts] holds the names the incoming copies landed under, so that the user can be told
 * where to look: a file changed on both sides is never merged, both versions are kept.
 */
data class SyncSummary(
    val downloaded: Int = 0,
    val uploaded: Int = 0,
    val deletedLocally: Int = 0,
    val deletedRemotely: Int = 0,
    val conflicts: List<String> = emptyList(),
) {

    val hasChanges get() = downloaded > 0 || uploaded > 0 || deletedLocally > 0 || deletedRemotely > 0
}

enum class SyncFailureReason {

    /** The device is offline, or the service could not be reached. */
    NETWORK,

    /** The authorization was refused, revoked or has expired; connecting again is the only way out. */
    AUTHORIZATION,

    /** The library could not be read or written. */
    STORAGE,
    UNKNOWN,
}
