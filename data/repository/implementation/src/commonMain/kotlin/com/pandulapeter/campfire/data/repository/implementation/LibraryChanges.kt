/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.repository.implementation

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.koin.core.annotation.Single

/**
 * Says that the app itself has changed a song or setlist file - a save, a creation, an import, a rename, a deletion -
 * which is what schedules an automatic sync run (see `SyncRepositoryImpl.scheduleSynchronization`).
 *
 * Its own type rather than a flow on the song and setlist repositories, because the sync repository reads the library
 * through those two and they cannot know about it in turn. Only the two repositories announce anything: the files a
 * sync run writes go around them, so a run never schedules the next one, and the rescans and refreshes that follow it
 * read the library without changing it.
 *
 * Says nothing about which file changed, since a run compares the whole library anyway, and keeps only the latest
 * announcement for a collector that is behind, since ten of them mean no more than one.
 */
@Single
internal class LibraryChanges {

    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val changes = _changes.asSharedFlow()

    fun onLibraryChanged() {
        _changes.tryEmit(Unit)
    }
}
