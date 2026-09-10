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
    val email: String?
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
        val isSyncing: Boolean,
        /** Milliseconds since the epoch, null until the first run finishes. */
        val lastSyncedAt: Long?,
        val lastOutcome: SyncOutcome?
    ) : SyncState
}

sealed interface SyncOutcome {

    data class Success(val summary: SyncSummary) : SyncOutcome

    data class Failure(val reason: SyncFailureReason) : SyncOutcome
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
    val conflicts: List<String> = emptyList()
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
    UNKNOWN
}
