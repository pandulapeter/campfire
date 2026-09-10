package com.pandulapeter.campfire.data.repository.api

import com.pandulapeter.campfire.data.model.domain.SyncProviderId
import com.pandulapeter.campfire.data.model.domain.SyncState
import com.pandulapeter.campfire.data.source.remote.api.model.AuthorizationCompletionPage
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
     */
    suspend fun restore(): RestoreResult

    /**
     * What start up found.
     *
     * @param isConnected Whether the app ended up connected, which is what tells the caller a first run is worth
     *   starting.
     * @param didReturnFromAuthorization Whether this start up was the answer to a consent page the app had been
     *   sent away to. Only ever true on the web, where the app stops existing while the user is on that page - it
     *   is what lets the UI put them back where they pressed the button. True whether the service said yes or no:
     *   an authorization that failed is exactly the case where the user most needs to see the screen that says so,
     *   so this reports that the app came back, not that it came back connected.
     */
    data class RestoreResult(
        val isConnected: Boolean,
        val didReturnFromAuthorization: Boolean
    )

    /**
     * Opens the service's consent page and connects if the user agrees. Returns whether it is now connected.
     *
     * @param completionPage What the page the browser lands on after consent should say. It is the one piece of
     *   Campfire's own text that lives outside the app, so it is handed down from the UI rather than written in the
     *   data layer, which has no access to the translations or to the language the user picked.
     */
    suspend fun connect(providerId: SyncProviderId, completionPage: AuthorizationCompletionPage): Boolean

    suspend fun disconnect()

    /**
     * Starts a run, if nothing is connected there is nothing to start, and if one is already going this does
     * nothing - so pressing the button twice cannot start two runs over the same files.
     *
     * Deliberately not suspend and returning nothing: a run outlives whoever asked for it, and what it is doing and
     * how it ended arrive through [syncState]. That is also what lets it carry on while the app is in the
     * background on Android, where the screen that started it may be gone.
     */
    fun synchronize()

    /** Stops a run where it is. What has already moved stays moved, and the next run picks up from there. */
    fun cancelSynchronization()
}
