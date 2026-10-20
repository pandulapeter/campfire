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
import kotlinx.coroutines.flow.emptyFlow
import org.koin.core.annotation.Single

/**
 * The JVM offers no way to hear of the connection coming back, short of polling the network interfaces or a host, so
 * the desktop's waits run their full length.
 */
@Single
internal class DesktopConnectivityMonitor : ConnectivityMonitor {

    override val reconnections = emptyFlow<Unit>()
}
