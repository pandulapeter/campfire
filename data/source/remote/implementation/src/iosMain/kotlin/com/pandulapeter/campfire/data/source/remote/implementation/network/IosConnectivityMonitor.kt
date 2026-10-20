/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.remote.implementation.network

import com.pandulapeter.campfire.data.source.remote.api.ConnectivityMonitor
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import org.koin.core.annotation.Single
import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_cancel
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.darwin.dispatch_get_main_queue

/** The path the system would use becoming satisfied, from `NWPathMonitor`, which needs no permission. */
@Single
internal class IosConnectivityMonitor : ConnectivityMonitor {

    override val reconnections = callbackFlow {
        val monitor = nw_path_monitor_create()
        // The first update is the state found at the start, which is not a reconnection.
        var isOnline: Boolean? = null
        nw_path_monitor_set_update_handler(monitor) { path ->
            val wasOnline = isOnline
            isOnline = nw_path_get_status(path) == nw_path_status_satisfied
            if (isOnline == true && wasOnline == false) trySend(Unit)
        }
        nw_path_monitor_set_queue(monitor, dispatch_get_main_queue())
        nw_path_monitor_start(monitor)
        awaitClose { nw_path_monitor_cancel(monitor) }
    }
}
