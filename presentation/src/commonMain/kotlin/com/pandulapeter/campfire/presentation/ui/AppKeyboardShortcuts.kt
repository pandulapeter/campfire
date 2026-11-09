/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.presentation.ui.components.shortcuts.KeyShortcut
import com.pandulapeter.campfire.presentation.ui.platform.rememberClipboardTextReader
import kotlinx.coroutines.launch

/**
 * Hears the app-wide shortcuts ([appShortcutOf]) on every platform with a keyboard, a hardware one on a phone or a
 * tablet included: at the root of the app, after everything under it (a field takes its own paste and Space, a focused
 * button its Space), and only the first press of a held key, since a held Space would start and stop the click thirty
 * times a second.
 *
 * Keys travel only along the focus path, and nothing on a list screen is focused while its search is closed, so the
 * root holds the focus itself whenever nothing under it does, a frame after it went: whatever took it has it by then.
 * Not while a dialog, a sheet or a menu is up, which hold their own and give it back as they go.
 * The shortcuts a platform's shell hears itself ([LocalKeysLeftToShell]) are left alone, so that none is handled twice.
 */
@Composable
internal fun Modifier.appKeyboardShortcuts(viewModel: CampfireViewModel): Modifier {
    val keysLeftToShell = LocalKeysLeftToShell.current
    val focusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()
    val clipboard = rememberClipboardTextReader()
    // Only the handlers read these, so they are no state.
    val heldKeys = remember { mutableSetOf<Key>() }
    val lastPress = remember { KeyPress() }
    val dialogHost = viewModel.dialogHost
    val visibleDialog by dialogHost.visibleDialog.collectAsStateWithLifecycle()
    val isUncovered by rememberUpdatedState(visibleDialog == null && !dialogHost.overlayState.isAnyMenuOpen)
    val windowInfo = LocalWindowInfo.current
    LaunchedEffect(windowInfo) { snapshotFlow { windowInfo.isWindowFocused }.collect { if (!it) heldKeys.clear() } }
    LaunchedEffect(focusRequester) {
        snapshotFlow { isUncovered }.collect { uncovered ->
            // A dialog, a sheet, or a menu holds the focus in a window of its own, where the root never loses it, so the
            // release of the keys that opened it (Ctrl / Cmd + slash) goes there, and still held here they would make the
            // next press of the same key read as a repeat.
            heldKeys.clear()
            if (uncovered && !lastPress.hasFocus) focusRequester.requestFocus()
        }
    }
    return this
        // In the preview pass and never consumed, so that a release whatever took the press still counts.
        .onPreviewKeyEvent { keyEvent ->
            when (keyEvent.type) {
                KeyEventType.KeyDown -> lastPress.isRepeat = !heldKeys.add(keyEvent.key)
                KeyEventType.KeyUp -> heldKeys.remove(keyEvent.key)
            }
            false
        }
        .onKeyEvent { keyEvent ->
            if (keyEvent.type != KeyEventType.KeyDown || lastPress.isRepeat) return@onKeyEvent false
            val shortcut = appShortcutOf(
                key = keyEvent.key,
                isCtrlOrMeta = keyEvent.isCtrlPressed || keyEvent.isMetaPressed,
                isShift = keyEvent.isShiftPressed,
                isAlt = keyEvent.isAltPressed,
            )
            shortcut != null && shortcut.keyShortcut !in keysLeftToShell && viewModel.perform(shortcut, clipboard)
        }
        .focusRequester(focusRequester)
        .onFocusChanged { focusState ->
            lastPress.hasFocus = focusState.hasFocus
            if (!focusState.hasFocus) {
                heldKeys.clear()
                coroutineScope.launch {
                    withFrameNanos {}
                    if (isUncovered && !lastPress.hasFocus) focusRequester.requestFocus()
                }
            }
        }
        .focusTarget()
}

/**
 * The shortcuts the platform's shell answers itself, which [appKeyboardShortcuts] leaves alone: the web's, whose browser
 * acts on a key before Compose hears it. Provided by the shell around the app.
 */
internal val LocalKeysLeftToShell = staticCompositionLocalOf { emptySet<KeyShortcut>() }

/** What the key handlers note between them about the last key-down and the focus, read only by them. */
private class KeyPress {
    var isRepeat = false
    var hasFocus = false
}
