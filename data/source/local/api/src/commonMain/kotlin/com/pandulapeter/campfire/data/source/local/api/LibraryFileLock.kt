/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.api

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

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
 * Rather than deadlock, [withLock] refuses to be entered again from a coroutine that holds it, or from a child started
 * inside its block, with an [IllegalStateException]. A coroutine launched into another scope does not carry the mark,
 * so a holder awaiting one that takes the lock still deadlocks.
 *
 * Here rather than in either of its two users, the repositories and sync, since they are modules of their own; one
 * instance for the app, provided by `DataLocalSourceModule`. Nothing above the data layer sees this module.
 */
class LibraryFileLock {

    private val mutex = Mutex()

    /**
     * This instance's own, not shared by every lock: a test builds several (one per device in a two-device sync test),
     * and a shared mark would make holding one refuse another, or a nested other lock replace the mark and hide a
     * reentry of this one.
     */
    private val heldKey = object : CoroutineContext.Key<Held> {}

    /** Marks a coroutine holding this lock. `withContext` with no dispatcher does not dispatch, so the mark costs one allocation. */
    private inner class Held : AbstractCoroutineContextElement(heldKey)

    suspend fun <T> withLock(block: suspend () -> T): T {
        check(currentCoroutineContext()[heldKey] == null) { "LibraryFileLock is not reentrant: this coroutine already holds it." }
        return mutex.withLock { withContext(Held()) { block() } }
    }
}
