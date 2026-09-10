package com.pandulapeter.campfire.data.repository.api

import com.pandulapeter.campfire.data.model.domain.SyncOutcome
import com.pandulapeter.campfire.data.model.domain.SyncProviderId
import com.pandulapeter.campfire.data.model.domain.SyncState
import kotlinx.coroutines.flow.Flow

/**
 * Keeping the library in step with a cloud service the user connected.
 *
 * Unlike the other repositories this one has no cached list to hand out: what it holds is a state machine, and the
 * library itself keeps living in the song and setlist repositories. A run that changed files on disk therefore has
 * to be followed by a rescan of those, which is the job of `SynchronizeLibraryUseCase`.
 */
interface SyncRepository {

    val syncState: Flow<SyncState>

    /** The services this build can connect to. Empty when the build was not given the credentials for any. */
    val availableProviders: List<SyncProviderId>

    /**
     * Reads the stored credentials and finishes an authorization that was started before the app was last closed,
     * which is the ordinary case on the web: consent happens on another page, and the app starts again afterwards.
     *
     * @return Whether the app ended up connected, which is what tells the caller a first run is worth starting.
     */
    suspend fun restore(): Boolean

    /** Opens the service's consent page and connects if the user agrees. Returns whether it is now connected. */
    suspend fun connect(providerId: SyncProviderId): Boolean

    suspend fun disconnect()

    /**
     * One sync run. Null when nothing is connected or a run is already going, so that pressing the button twice
     * does not start two runs over the same files.
     */
    suspend fun synchronize(): SyncOutcome?
}
