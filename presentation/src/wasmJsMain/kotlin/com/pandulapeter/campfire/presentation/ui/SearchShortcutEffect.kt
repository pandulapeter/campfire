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
 * Opens the search of the list screen that is on top on Ctrl / Cmd + F (`ScreenSearches.openCurrentSearch`), and
 * keeps the browser's own find bar shut when it does: that bar searches the page's text, and the whole of this page is
 * one canvas with none. With the song editor on top, Ctrl / Cmd + F and R are the editor's Find and Find and replace
 * (`editorSearchShortcuts`), and B, I, and U its Bold, Italic, and Underline (`editorFormattingShortcuts`), so there the
 * browser is kept from its find bar, from reloading the page under the text being typed, and from its bookmarks, page
 * info, and page source, and the key goes on to Compose. Everywhere else - another screen, a dialog - the key is left alone and
 * the browser opens its bar as it always does.
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
        } else if (isEditorShortcutKey(keyEvent) && viewModel.navigator.backStack.lastOrNull() is CampfireDestination.SongEditor) {
            keyEvent.preventDefault()
        }
    }
    window.addEventListener(EVENT_KEY_DOWN, listener, true)
    onDispose { window.removeEventListener(EVENT_KEY_DOWN, listener, true) }
}

/**
 * Ctrl / Cmd + F, R, B, I, or U without Alt, by the physical key as Compose goes by it, or by the character a virtual
 * keyboard sends.
 */
private fun isEditorShortcutKey(keyEvent: KeyboardEvent) = (keyEvent.ctrlKey || keyEvent.metaKey) && !keyEvent.altKey &&
    (keyEvent.code in EDITOR_KEY_CODES || keyEvent.key.lowercase() in EDITOR_KEYS)

private val EDITOR_KEYS = setOf("f", "r", "b", "i", "u")
private val EDITOR_KEY_CODES = EDITOR_KEYS.map { "Key${it.uppercase()}" }.toSet()
