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

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.annotation.Single

/**
 * Held by every change the app makes to a song or setlist file, from whatever it checks the file against to the write
 * that follows, so that a sync run and the song and setlist repositories never interleave on one file. The sync engine
 * decides whether it may write a download or delete a file on what the file holds, and a save that landed between that
 * read and the write would be overwritten, or deleted, with the save reported as done and no conflict copy to show for
 * it. Under this lock the save lands either before the check, which then sees it, or after the write, like any save.
 *
 * Only ever held around local reads and writes, never around a request: a run waits for the network with it free, so
 * an editor's save never waits on a transfer. It is not reentrant, so nothing that holds it may call anything that
 * takes it - the repositories take their own locks first and this one inside them, and the engine takes no other.
 */
@Single
internal class LibraryFileLock {

    private val mutex = Mutex()

    suspend fun <T> withLock(block: suspend () -> T): T = mutex.withLock { block() }
}
