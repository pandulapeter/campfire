/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songEditor

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/**
 * Answers Ctrl / Cmd + F with [onFind] and Ctrl / Cmd + R with [onReplace], on the editor and on its search sheet alike,
 * so the key opens either sheet from the text and switches between them from inside one. Like `saveShortcut` it hears
 * the key only along the focus path, and not with Alt held, since AltGr arrives as Ctrl + Alt on Windows and the web and
 * types a character with these keys on some layouts. The key-up is taken too, so that nothing under this sees half a
 * shortcut. On the web the browser's own find bar and reload are kept from these keys by the shell, while the editor is
 * on top, since a key pressed in the hidden input of a text field reaches Compose only after the browser has acted on it.
 */
internal fun Modifier.editorSearchShortcuts(onFind: () -> Unit, onReplace: () -> Unit) = onPreviewKeyEvent { keyEvent ->
    val isShortcut = (keyEvent.isCtrlPressed || keyEvent.isMetaPressed) && !keyEvent.isAltPressed
    val action = when {
        !isShortcut -> null
        keyEvent.key == Key.F -> onFind
        keyEvent.key == Key.R -> onReplace
        else -> null
    }
    if (action != null && keyEvent.type == KeyEventType.KeyDown) action()
    action != null
}
