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

package com.pandulapeter.campfire.presentation.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.await
import kotlin.js.Promise

/**
 * `navigator.permissions.query`, asked again on every resume, unknown where the browser has no answer for the
 * microphone. Opening the input is what asks, and where the browser keeps a refusal is its own site settings, next to
 * the address, which no page can open.
 */
@Composable
internal actual fun rememberMicrophonePermission(): MicrophonePermission {
    var status by remember { mutableStateOf(lastKnownStatus) }
    var checks by remember { mutableStateOf(0) }
    LifecycleResumeEffect(Unit) {
        checks++
        onPauseOrDispose { }
    }
    LaunchedEffect(checks) { status = refreshMicrophoneStatus() }
    return MicrophonePermission(
        status = status,
        canAskAgain = status != MicrophoneStatus.DENIED,
        request = null,
        openSettings = null,
        isAllowedInBrowser = true,
    )
}

/**
 * The answer of the last query, which the tuner's first frame starts from: the query is a promise, and a first frame
 * that showed the notice of a microphone already allowed would have it flash in and out. `CampfireWebApp` asks once as
 * the app starts, for the first time the tab is opened.
 */
private var lastKnownStatus = MicrophoneStatus.UNKNOWN

/** Asks the browser again and keeps the answer, see [lastKnownStatus]. */
internal suspend fun refreshMicrophoneStatus() = when (queryMicrophonePermission().await<JsString>().toString()) {
    "granted" -> MicrophoneStatus.GRANTED
    "denied" -> MicrophoneStatus.DENIED
    "prompt" -> MicrophoneStatus.NOT_ASKED
    else -> MicrophoneStatus.UNKNOWN
}.also { lastKnownStatus = it }

private fun queryMicrophonePermission(): Promise<JsString> = js(
    """(function () {
        try {
            if (!navigator.permissions || !navigator.permissions.query) return Promise.resolve('unknown');
            return navigator.permissions.query({ name: 'microphone' }).then(function (result) { return result.state; }, function () { return 'unknown'; });
        } catch (error) {
            return Promise.resolve('unknown');
        }
    })()"""
)
