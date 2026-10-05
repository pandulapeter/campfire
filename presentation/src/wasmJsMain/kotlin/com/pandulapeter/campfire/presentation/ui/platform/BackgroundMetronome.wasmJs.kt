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

import kotlinx.coroutines.await
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.Promise

/**
 * The browser's media session for a playing click, where the browser has one: the title and the tempo in whatever the
 * browser shows for a page playing sound, and its pause and stop. A best effort - browsers show a session reliably
 * only for a media element, and Web Audio is not one - which is also why nothing depends on it.
 *
 * Set by the composition, which has the words in the app's language, and cleared by it - and also by the web shell on
 * the engine's own stop, since a hidden tab's composition stops collecting, and a hidden tab is when the browser's
 * media controls are used.
 */
internal object WebMetronomeNotifier : MetronomeNotifier {

    override fun onMetronomeNotificationChanged(notification: MetronomeNotification?) {
        if (notification == null) clearMediaSession() else setMediaSession(notification.title, notification.body)
    }

    /**
     * Calls [onStop] whenever the media session's pause or stop is pressed, for as long as the caller is not cancelled.
     * A Kotlin lambda cannot be handed to a `js(...)` block, so every press comes back as a promise of its own.
     */
    suspend fun forEachStopRequest(onStop: () -> Unit) {
        while (true) {
            awaitMediaSessionStop().await<JsAny?>()
            onStop()
        }
    }
}

private fun setMediaSession(title: String, body: String): Unit = js(
    """{
        if (!('mediaSession' in navigator) || typeof MediaMetadata === 'undefined') return;
        navigator.mediaSession.metadata = new MediaMetadata({ title: title, artist: body });
        navigator.mediaSession.playbackState = 'playing';
    }"""
)

private fun clearMediaSession(): Unit = js(
    """{
        if (!('mediaSession' in navigator)) return;
        navigator.mediaSession.metadata = null;
        navigator.mediaSession.playbackState = 'none';
    }"""
)

private fun awaitMediaSessionStop(): Promise<JsAny?> = js(
    """new Promise(function (resolve) {
        if (!('mediaSession' in navigator)) return;
        ['pause', 'stop'].forEach(function (action) {
            try {
                navigator.mediaSession.setActionHandler(action, function () { resolve(null); });
            } catch (error) {
                // An action this browser does not know is simply not offered.
            }
        });
    })"""
)
