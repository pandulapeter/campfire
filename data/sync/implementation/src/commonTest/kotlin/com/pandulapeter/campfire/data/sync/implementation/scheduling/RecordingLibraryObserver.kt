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
 * Stands in for whatever an app keeps of its library, and records what sync told it: which files to read again, in the
 * order the refreshes reached it, and how many whole rescans. [onRefresh] runs before a refresh is recorded, which is
 * where a test holds one up.
 */
internal class RecordingLibraryObserver(
    private val onRefresh: suspend (Set<SyncKey>) -> Unit = {},
) : SyncLibraryObserver {

    val refreshed = mutableListOf<SyncKey>()
    var rescanCount = 0
    var firstReadCount = 0

    override suspend fun awaitFirstRead() {
        firstReadCount++
    }

    override suspend fun refresh(keys: Set<SyncKey>) {
        onRefresh(keys)
        refreshed += keys
    }

    override suspend fun rescan() {
        rescanCount++
    }
}
