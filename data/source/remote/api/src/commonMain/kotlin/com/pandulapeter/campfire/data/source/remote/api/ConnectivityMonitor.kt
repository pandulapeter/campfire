/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.remote.api

import kotlinx.coroutines.flow.Flow

/**
 * The device getting its connection back, for what is waiting to try a request again: a wait sized for a long outage
 * is cut short the moment the outage is over. Read from what the platform offers without asking for a permission, and
 * never a request of its own.
 */
interface ConnectivityMonitor {

    /**
     * Emits each time the device goes from having no usable connection to having one, while collected: the platform is
     * only listened to then. Nothing is emitted for the state found when collection starts, so a request that failed
     * for a reason other than the device being offline is not tried again at once. A platform that cannot tell never
     * emits, and its waits run their full length.
     */
    val reconnections: Flow<Unit>
}
