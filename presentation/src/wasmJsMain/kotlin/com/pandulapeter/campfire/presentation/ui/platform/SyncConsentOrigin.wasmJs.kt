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

import com.pandulapeter.campfire.presentation.ui.sync.SyncConsentOrigin
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.js.ExperimentalWasmJsInterop

/**
 * Kept in the tab's session storage, which outlives the navigation to the consent page and back, as nothing else of the
 * app does: the answer lands on the folder's address with a back stack of its own, and the onboarding is never part of
 * an address to begin with. Storage that cannot be written - blocked for the site - shows the answer in Settings.
 */
internal actual fun keepSyncConsentOrigin(origin: SyncConsentOrigin) = writeSessionValue(Json.encodeToString(origin))

/** Null for nothing kept, and for what a build that wrote it differently kept. */
internal actual fun takeSyncConsentOrigin(): SyncConsentOrigin? = takeSessionValue()?.let { json ->
    try {
        Json.decodeFromString<SyncConsentOrigin>(json)
    } catch (exception: SerializationException) {
        null
    }
}

private fun writeSessionValue(value: String) {
    js(
        """{
            try {
                window.sessionStorage.setItem('campfire-sync-consent-origin', value);
            } catch (e) {
            }
        }"""
    )
}

private fun takeSessionValue(): String? = js(
    """(function () {
        try {
            var value = window.sessionStorage.getItem('campfire-sync-consent-origin');
            window.sessionStorage.removeItem('campfire-sync-consent-origin');
            return value;
        } catch (e) {
            return null;
        }
    })()"""
)
