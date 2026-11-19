/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.sync.implementation.scheduling

import com.pandulapeter.campfire.data.sync.implementation.SyncKey

/**
 * Whatever the app keeps of the library it shows, which a run's writes go around: the one thing sync has to tell that the
 * files changed. [SyncLibraryRefresher] decides when (a run's start and end, and throttled while it goes); this decides
 * what reading the files again means for the app.
 */
internal interface SyncLibraryObserver {

    /**
     * Returns once the app has read the library for the first time, or has failed to: a run reads every file too, so the
     * app's own read goes first. Waits for that read rather than starting it again.
     */
    suspend fun awaitFirstRead()

    /**
     * Reads [keys] again, the files a run wrote or deleted. May be cancelled partway, after which the caller hands the
     * same keys over again, so reading one twice must be harmless.
     */
    suspend fun refresh(keys: Set<SyncKey>)

    /** Reads the whole library again, for a run cut off where what it changed is not known for certain. */
    suspend fun rescan()
}
