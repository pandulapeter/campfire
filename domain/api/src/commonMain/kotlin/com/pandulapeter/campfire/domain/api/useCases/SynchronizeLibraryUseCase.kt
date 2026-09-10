package com.pandulapeter.campfire.domain.api.useCases

interface SynchronizeLibraryUseCase {

    /**
     * Starts a run and returns. What it is doing, and how it ended, arrive through [GetSyncStateUseCase] - a run
     * belongs to the app rather than to the screen that asked for it, and outlives both the screen and, on Android,
     * the activity.
     *
     * Does nothing when nothing is connected or when a run is already going.
     */
    operator fun invoke()
}

interface CancelSynchronizationUseCase {

    /** Stops a run where it is. What has already moved stays moved, and the next run picks up from there. */
    operator fun invoke()
}
