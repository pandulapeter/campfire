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
import kotlinx.browser.window
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.unsafeCast

/**
 * Opens the search of the list screen that is on top on Ctrl / Cmd + F ([CampfireViewModel.openCurrentSearch]), and
 * keeps the browser's own find bar shut when it does: that bar searches the page's text, and the whole of this page is
 * one canvas with none. Everywhere else - another screen, a dialog - the key is left alone and the browser opens its
 * bar as it always does.
 *
 * It is a listener on the window in the capture phase rather than a key handler inside the composition, for two
 * reasons. Nothing on a list screen is focused until its search is, and a key event only reaches Compose along the
 * focus path. And a key pressed in the hidden `<input>` that holds the caret of a field reaches Compose only after
 * the browser has acted on it (see [startSuppressingBrowserSave]), so a Compose handler could never have stopped the
 * find bar opening over the app. The listener is a Kotlin function rather than a `js(...)` block because what it has
 * to decide is the view model's, and has to be decided before the browser moves on.
 */
@Composable
internal fun SearchShortcutEffect(viewModel: CampfireViewModel) = DisposableEffect(viewModel) {
    val listener: (Event) -> Unit = { event ->
        val keyEvent = event.unsafeCast<KeyboardEvent>()
        // code, as Compose goes by it (Key.F is the physical key); key for a virtual keyboard, which has none.
        val isF = keyEvent.code == "KeyF" || keyEvent.key == "f" || keyEvent.key == "F"
        val shortcut = if (isF) appShortcutOf(ShortcutKey.F, isCtrlOrMeta = keyEvent.ctrlKey || keyEvent.metaKey, isAlt = keyEvent.altKey) else null
        if (shortcut is AppShortcut.OpenSearch && viewModel.perform(shortcut)) {
            keyEvent.preventDefault()
        }
    }
    window.addEventListener(EVENT_KEY_DOWN, listener, true)
    onDispose { window.removeEventListener(EVENT_KEY_DOWN, listener, true) }
}
