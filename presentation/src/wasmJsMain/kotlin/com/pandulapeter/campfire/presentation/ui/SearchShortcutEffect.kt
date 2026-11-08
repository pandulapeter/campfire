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
 * (`editorSearchShortcuts`), and B, I, and U its Bold, Italic, and Underline (`editorFormattingShortcuts`), Y its redo, and
 * F3 its search's steps, so there the browser is kept from its find bar, from reloading the page under the text being
 * typed, from its bookmarks, page info, page source, and history, and the key goes on to Compose. Everywhere else - another screen, a dialog - the key is left alone and
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
        if (keyEvent.isShortcut(KeyShortcut.FIND) && viewModel.navigator.searches.openCurrentSearch()) {
            keyEvent.preventDefault()
        } else if (EDITOR_SHORTCUTS.any(keyEvent::isShortcut) && viewModel.navigator.backStack.lastOrNull() is CampfireDestination.SongEditor) {
            keyEvent.preventDefault()
        }
    }
    window.addEventListener(EVENT_KEY_DOWN, listener, true)
    onDispose { window.removeEventListener(EVENT_KEY_DOWN, listener, true) }
}

/**
 * The editor's shortcuts the browser has a use of its own for. B, I, and U only without Shift, as the editor hears
 * them, so the browser keeps Ctrl + Shift + I (its developer tools) and the others it gives those keys. Ctrl / Cmd + Y
 * is the editor's redo, the browser's history on a Mac; F3 its search's next match, the browser's find next.
 */
private val EDITOR_SHORTCUTS = listOf(
    KeyShortcut.FIND,
    KeyShortcut.REPLACE,
    KeyShortcut.BOLD,
    KeyShortcut.ITALIC,
    KeyShortcut.UNDERLINE,
    KeyShortcut.REDO_ALTERNATE,
    KeyShortcut.FIND_NEXT,
    KeyShortcut.FIND_PREVIOUS,
)
