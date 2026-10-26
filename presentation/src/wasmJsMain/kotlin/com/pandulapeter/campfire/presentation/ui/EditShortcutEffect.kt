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

package com.pandulapeter.campfire.presentation.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import kotlinx.browser.window
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.unsafeCast

/**
 * Keeps the browser from Ctrl / Cmd + E with the song details screen on top outside performance mode, where the key opens
 * the editor (`songKeyboardShortcuts`): Chrome and Edge would otherwise move the focus to their address bar, away from the
 * page, as the editor opens. The key goes on to Compose, since only its default is prevented. A listener on the window in
 * the capture phase for the same reason as [SearchShortcutEffect]'s.
 */
@Composable
internal fun EditShortcutEffect(viewModel: CampfireViewModel) = DisposableEffect(viewModel) {
    val listener: (Event) -> Unit = { event ->
        val keyEvent = event.unsafeCast<KeyboardEvent>()
        // code, as Compose goes by it (Key.E is the physical key); key for a virtual keyboard, which has none.
        val isEdit = (keyEvent.ctrlKey || keyEvent.metaKey) && !keyEvent.altKey && (keyEvent.code == "KeyE" || keyEvent.key.lowercase() == "e")
        val top = viewModel.navigator.backStack.lastOrNull()
        if (isEdit && top is CampfireDestination.SongDetails && top.importPreview == null &&
            !viewModel.preferencesController.isPerformanceModeEnabled.value
        ) {
            keyEvent.preventDefault()
        }
    }
    window.addEventListener(EVENT_KEY_DOWN, listener, true)
    onDispose { window.removeEventListener(EVENT_KEY_DOWN, listener, true) }
}
