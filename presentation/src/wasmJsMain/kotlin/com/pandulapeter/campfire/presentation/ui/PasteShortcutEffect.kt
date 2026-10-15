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
import kotlin.js.ExperimentalWasmJsInterop

/**
 * Makes a new song of the text pasted with Ctrl / Cmd + V on the song list, as Paste from clipboard does
 * (`PastedSongController.isNewSongPasteAvailable` says where). It listens for the browser's `paste` rather than for the
 * key, because the event carries the text with no permission asked and no prompt shown, where reading the clipboard
 * from the key would ask. A paste into a focused field - the hidden `<input>` a focused Compose field types into, or
 * anything else editable - is the field's and left alone, and so is one anywhere else the list is not on top.
 *
 * In the capture phase on the window, for the reasons [SearchShortcutEffect] gives.
 */
@Composable
internal fun PasteShortcutEffect(viewModel: CampfireViewModel) = DisposableEffect(viewModel) {
    val listener: (Event) -> Unit = { event ->
        if (!isEditableTarget(event) && viewModel.pastedSongController.isNewSongPasteAvailable) {
            event.preventDefault()
            viewModel.pastedSongController.newSongFromText(pastedText(event).orEmpty())
        }
    }
    window.addEventListener(EVENT_PASTE, listener, true)
    onDispose { window.removeEventListener(EVENT_PASTE, listener, true) }
}

private fun isEditableTarget(event: Event): Boolean = js(
    """(function () {
        var target = event.target;
        if (!target || !target.tagName) return false;
        var tag = target.tagName.toLowerCase();
        return tag === 'input' || tag === 'textarea' || target.isContentEditable === true;
    })()"""
)

private fun pastedText(event: Event): String? = js("event.clipboardData ? event.clipboardData.getData('text/plain') : null")

private const val EVENT_PASTE = "paste"
