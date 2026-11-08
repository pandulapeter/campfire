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
import com.pandulapeter.campfire.presentation.ui.components.shortcuts.KeyShortcut
import kotlinx.browser.window
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.unsafeCast

/**
 * Ctrl / Cmd + 1 to 6 and comma select a main screen (`selectScreenByKey`), and Ctrl / Cmd + A selects every song listed
 * in the songs screen's selection mode (`selectAllSongsByKey`), each keeping the browser from its own use of the key
 * (switching tabs, its settings on a Mac, selecting the page's text) only where the app did something with it: anywhere
 * else the browser has the key as it always does. Select all is left to a field being typed in, which selects its own
 * text. A listener on the window in the capture phase for the reasons [SearchShortcutEffect] gives; the app's own root
 * handler leaves these keys alone ([LocalKeysLeftToShell]).
 */
@Composable
internal fun ScreenShortcutEffect(viewModel: CampfireViewModel) = DisposableEffect(viewModel) {
    val listener: (Event) -> Unit = listener@{ event ->
        val keyEvent = event.unsafeCast<KeyboardEvent>()
        val shortcut = SHELL_SCREEN_SHORTCUTS.firstOrNull(keyEvent::isShortcut) ?: return@listener
        val isDone = when (shortcut) {
            KeyShortcut.SELECT_ALL -> !isTypingTarget(keyEvent) && viewModel.selectAllSongsByKey()
            else -> viewModel.selectScreenByKey(shortcut)
        }
        if (isDone) keyEvent.preventDefault()
    }
    window.addEventListener(EVENT_KEY_DOWN, listener, true)
    onDispose { window.removeEventListener(EVENT_KEY_DOWN, listener, true) }
}

/** The shortcuts [ScreenShortcutEffect] answers, which [CampfireWebApp] leaves to it. */
internal val SHELL_SCREEN_SHORTCUTS get() = KeyShortcut.screens + KeyShortcut.SETTINGS + KeyShortcut.SELECT_ALL
