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

import com.pandulapeter.campfire.data.model.share.ShareLinks
import com.pandulapeter.campfire.data.model.share.SharedLink
import kotlinx.coroutines.await
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsString
import kotlin.js.Promise

/**
 * The shared song and setlist links the page was opened with, or that another tab handed over, as index.html keeps them:
 * a list in session storage, taken out of the address before anything of the app ran. An entry leaves the list only
 * once it has been handed on, so a page reloaded before that still has it, and one handed on is never imported again
 * by a reload. A payload `ShareLinks.parse` does not read is dropped all the same.
 */
internal fun sharedLinks(): Flow<SharedLink> = flow {
    while (true) {
        val payload = nextSharedLink().await<JsString>().toString()
        ShareLinks.parse(payload)?.let { emit(it) }
        takeSharedLink(payload)
    }
}.catch { exception ->
    // Collected at the root of the app, like droppedFiles, which this would otherwise take down with it.
    println("Could not read the shared link: ${exception.message}")
}

private fun nextSharedLink(): Promise<JsString> = js(
    "window.campfireSharedLinks ? window.campfireSharedLinks.next() : new Promise(function () {})"
)

private fun takeSharedLink(payload: String) {
    js("window.campfireSharedLinks && window.campfireSharedLinks.take(payload)")
}
