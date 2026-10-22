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

import com.pandulapeter.campfire.data.source.remote.api.ConnectivityMonitor
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration

/**
 * Waits [duration], or less if the device gets its connection back in the meantime: what a network failure waits, and
 * answers whether it was the reconnection that ended the wait. A monitor whose flow ends without emitting (the desktop's
 * is empty) still waits the full length, since an exception thrown here would end the scheduler's or the change feed's
 * loop for the rest of the process.
 */
internal suspend fun ConnectivityMonitor.awaitReconnection(duration: Duration): Boolean = withTimeoutOrNull(duration) {
    reconnections.firstOrNull() ?: awaitCancellation()
    true
} ?: false
