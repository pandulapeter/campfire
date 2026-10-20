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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration

/** Waits [duration], or less if the device gets its connection back in the meantime: what a network failure waits. */
internal suspend fun ConnectivityMonitor.awaitReconnection(duration: Duration) {
    withTimeoutOrNull(duration) { reconnections.first() }
}
