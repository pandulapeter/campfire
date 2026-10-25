/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
@file:OptIn(ExperimentalWasmJsInterop::class)

package com.pandulapeter.campfire.data.source.remote.implementation.network

import com.pandulapeter.campfire.data.source.remote.api.ConnectivityMonitor
import kotlin.js.ExperimentalWasmJsInterop
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import org.koin.core.annotation.Single

/**
 * The browser's `online` event, which needs no permission. A browser says online for any network, a captive portal's
 * included, so a reconnection can still fail, and the wait after it starts over.
 */
@Single
internal class WebConnectivityMonitor : ConnectivityMonitor {

    override val reconnections = callbackFlow {
        val listener = addOnlineListener { trySend(Unit) }
        awaitClose { removeOnlineListener(listener) }
    }
}

/** Returns the listener added, which is what removing it takes. */
private fun addOnlineListener(onOnline: () -> Unit): JsAny =
    js("{ const listener = () => onOnline(); window.addEventListener('online', listener); return listener; }")

private fun removeOnlineListener(listener: JsAny): Unit = js("window.removeEventListener('online', listener)")
