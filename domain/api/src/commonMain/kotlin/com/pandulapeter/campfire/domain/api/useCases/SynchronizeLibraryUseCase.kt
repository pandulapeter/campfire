package com.pandulapeter.campfire.domain.api.useCases

import com.pandulapeter.campfire.data.model.domain.SyncOutcome

interface SynchronizeLibraryUseCase {

    /**
     * One sync run, followed by a rescan of whatever it changed. Null when nothing is connected, or when a run is
     * already going: pressing the button twice must not start two runs over the same files.
     */
    suspend operator fun invoke(): SyncOutcome?
}
