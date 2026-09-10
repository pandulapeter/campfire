/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
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
