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
 * Space and M start and stop the metronome ([CampfireViewModel.toggleMetronomeByKey]). Unlike the search shortcut this
 * listens in the bubbling phase and leaves alone whatever Compose already handled (a focused button, which Space
 * presses) or what is typed into a field (the hidden input of a focused text field), so that the key only toggles the
 * metronome where it would otherwise do nothing.
 */
@Composable
internal fun MetronomeShortcutEffect(viewModel: CampfireViewModel) = DisposableEffect(viewModel) {
    val listener: (Event) -> Unit = listener@{ event ->
        val keyEvent = event.unsafeCast<KeyboardEvent>()
        if (keyEvent.defaultPrevented || isTypingTarget(keyEvent)) return@listener
        val key = when (keyEvent.code) {
            "Space" -> ShortcutKey.SPACE
            "KeyM" -> ShortcutKey.M
            else -> return@listener
        }
        val shortcut = appShortcutOf(key, isCtrlOrMeta = keyEvent.ctrlKey || keyEvent.metaKey, isAlt = keyEvent.altKey)
        // A held key repeats its key-down; only the first press is a request, the rest would start and stop the click
        // thirty times a second.
        if (shortcut !is AppShortcut.ToggleMetronome || keyEvent.repeat) return@listener
        if (viewModel.perform(shortcut)) keyEvent.preventDefault()
    }
    window.addEventListener(EVENT_KEY_DOWN, listener)
    onDispose { window.removeEventListener(EVENT_KEY_DOWN, listener) }
}

private fun isTypingTarget(event: KeyboardEvent): Boolean = js("event.target && (event.target.tagName === 'INPUT' || event.target.tagName === 'TEXTAREA' || event.target.isContentEditable === true)")
