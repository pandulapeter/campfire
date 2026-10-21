/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.platform

import android.content.Context
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * The referrer Play installed this app with, asked of the Play Store app over IPC, or null where there is none to be
 * had: an installation from anywhere but Play, a device without the Play Store, or a Play Store too old to answer
 * (`FEATURE_NOT_SUPPORTED`, `SERVICE_UNAVAILABLE`). Nothing is sent: Play answers with what the store page was opened
 * with. A Play Store that never answers is given up on after a while, so the connection is not held open for good.
 */
internal suspend fun readInstallReferrer(context: Context): String? = withContext(Dispatchers.IO) {
    val client = InstallReferrerClient.newBuilder(context).build()
    try {
        val responseCode = withTimeoutOrNull(INSTALL_REFERRER_TIMEOUT_MILLIS) {
            suspendCancellableCoroutine { continuation ->
                client.startConnection(
                    object : InstallReferrerStateListener {
                        override fun onInstallReferrerSetupFinished(responseCode: Int) {
                            if (continuation.isActive) continuation.resume(responseCode)
                        }

                        override fun onInstallReferrerServiceDisconnected() {
                            if (continuation.isActive) continuation.resume(InstallReferrerClient.InstallReferrerResponse.SERVICE_DISCONNECTED)
                        }
                    },
                )
            }
        }
        if (responseCode == InstallReferrerClient.InstallReferrerResponse.OK) client.installReferrer.installReferrer else null
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        println("Could not read the install referrer: ${exception.message}")
        null
    } finally {
        client.endConnection()
    }
}

private const val INSTALL_REFERRER_TIMEOUT_MILLIS = 10_000L
