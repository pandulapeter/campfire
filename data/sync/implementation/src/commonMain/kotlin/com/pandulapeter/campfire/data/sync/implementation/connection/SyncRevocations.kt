/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.sync.implementation.connection

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The requests that tell a service to drop the grant of a connection the user has already seen go, run in [scope]
 * after the disconnect: like a run, they belong to the app, and leaving the screen does not stop them. A new
 * authorization cancels whichever is still going, so that a late revocation of the old grant can never meet a
 * connection made seconds after it. One cut short that way leaves the old grant to expire on its own.
 */
internal class SyncRevocations(private val scope: CoroutineScope) {

    /** Guarded by [mutex]: disconnecting and connecting can be asked for from different threads. */
    private val jobs = mutableListOf<Job>()
    private val mutex = Mutex()

    suspend fun start(revocations: List<suspend () -> Unit>) = mutex.withLock {
        jobs.removeAll { !it.isActive }
        revocations.forEach { revocation -> jobs += scope.launch { revocation() } }
    }

    suspend fun cancelAll() {
        val going = mutex.withLock { jobs.toList().also { jobs.clear() } }
        going.forEach { it.cancelAndJoin() }
    }
}
