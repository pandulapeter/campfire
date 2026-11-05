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
 * M starts and stops the metronome (`MetronomeController.toggleMetronomeByKey`). Unlike the search shortcut this listens
 * in the bubbling phase and leaves alone whatever Compose already handled or what is typed into a field (the hidden
 * input of a focused text field), so that the key only toggles the metronome where it would otherwise do nothing.
 */
@Composable
internal fun MetronomeShortcutEffect(viewModel: CampfireViewModel) = DisposableEffect(viewModel) {
    val listener: (Event) -> Unit = listener@{ event ->
        val keyEvent = event.unsafeCast<KeyboardEvent>()
        if (keyEvent.defaultPrevented || isTypingTarget(keyEvent)) return@listener
        if (keyEvent.code != "KeyM") return@listener
        val shortcut = KeyShortcut.METRONOME
        val isShortcut = shortcut.chord.matches(
            key = shortcut.chord.key,
            isCtrlOrMeta = keyEvent.ctrlKey || keyEvent.metaKey,
            isShift = keyEvent.shiftKey,
            isAlt = keyEvent.altKey,
        )
        // A held key repeats its key-down; only the first press is a request, the rest would start and stop the click
        // thirty times a second.
        if (!isShortcut || keyEvent.repeat) return@listener
        if (viewModel.metronomeController.toggleMetronomeByKey()) keyEvent.preventDefault()
    }
    window.addEventListener(EVENT_KEY_DOWN, listener)
    onDispose { window.removeEventListener(EVENT_KEY_DOWN, listener) }
}

private fun isTypingTarget(event: KeyboardEvent): Boolean = js("event.target && (event.target.tagName === 'INPUT' || event.target.tagName === 'TEXTAREA' || event.target.isContentEditable === true)")
