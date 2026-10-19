/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.update

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Whether the device's default network is one Android has validated, which is what decides whether a required update
 * can be waited out (see [AppUpdateController.canContinueOffline]).
 *
 * A validated network is what is trusted, not merely a connected one: a captive portal, or a network whose validation
 * probe is blocked, reads as offline, which offers a way on where Play could perhaps still work, and that is the
 * user's choice to make. The first value is read as this is created, so the blocking screen's first frame already
 * has the right buttons. The callback is delivered on the main looper, so every write of [isOnline], which is
 * Compose state, happens on the main thread.
 *
 * @param onBecameOnline Called as the device goes from offline to online, once per change: Android repeats the
 *   capabilities of the same network as other details of it change.
 */
@Stable
internal class DefaultNetworkWatcher(
    context: Context,
    private val onBecameOnline: () -> Unit,
) {

    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
    private var isRegistered = false
    private val callback = object : ConnectivityManager.NetworkCallback() {

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) =
            onConnectionChanged(isOnline = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))

        override fun onLost(network: Network) = onConnectionChanged(isOnline = false)
    }

    /** True where the device's state cannot be read, which leaves a required update the way it is without this. */
    var isOnline by mutableStateOf(
        connectivityManager?.let { manager ->
            manager.getNetworkCapabilities(manager.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        } ?: true,
    )
        private set

    fun start() {
        if (isRegistered || connectivityManager == null) return
        try {
            connectivityManager.registerDefaultNetworkCallback(callback, Handler(Looper.getMainLooper()))
            isRegistered = true
        } catch (exception: RuntimeException) {
            // A process holding too many callbacks is refused another, which only costs the offline way past.
        }
    }

    fun stop() {
        if (!isRegistered || connectivityManager == null) return
        isRegistered = false
        try {
            connectivityManager.unregisterNetworkCallback(callback)
        } catch (exception: IllegalArgumentException) {
            // Already unregistered, which is the state this was asked for.
        }
    }

    private fun onConnectionChanged(isOnline: Boolean) {
        val wasOnline = this.isOnline
        this.isOnline = isOnline
        if (isOnline && !wasOnline) onBecameOnline()
    }
}
