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

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.pandulapeter.campfire.data.source.remote.api.ConnectivityMonitor
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

/**
 * The default network becoming one Android has validated, through `ACCESS_NETWORK_STATE`, a permission granted at
 * install without a prompt (the app holds it for the required update screen already). Validated rather than merely
 * connected, so that a captive portal is not taken for the connection being back.
 */
@Single
internal class AndroidConnectivityMonitor(
    @Provided private val context: Context,
) : ConnectivityMonitor {

    override val reconnections = callbackFlow {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        if (connectivityManager == null) {
            awaitClose()
            return@callbackFlow
        }
        var isOnline = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)?.isValidated() == true
        val callback = object : ConnectivityManager.NetworkCallback() {

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                // Android repeats a network's capabilities as other details of it change, so only the change is told.
                val wasOnline = isOnline
                isOnline = networkCapabilities.isValidated()
                if (isOnline && !wasOnline) trySend(Unit)
            }

            override fun onLost(network: Network) {
                isOnline = false
            }
        }
        try {
            connectivityManager.registerDefaultNetworkCallback(callback)
        } catch (exception: RuntimeException) {
            // A process holding too many callbacks is refused another, which only leaves the waits their full length.
            awaitClose()
            return@callbackFlow
        }
        awaitClose {
            try {
                connectivityManager.unregisterNetworkCallback(callback)
            } catch (exception: IllegalArgumentException) {
                // Already unregistered, which is the state this was asked for.
            }
        }
    }

    private fun NetworkCapabilities.isValidated() = hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
