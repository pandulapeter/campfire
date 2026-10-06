/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components

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
 * Answers Ctrl / Cmd + S with [onSave], the same save as the button of whatever this is on - the editor, a sheet, the
 * export screen - for the hand that reaches for the keyboard instead. It hears the key only along the focus path, so
 * whatever it is on has to hold the focus itself (a bare focus target taken as it opens) for the key to work before
 * anything in it has been clicked. The key is consumed whether or not [onSave] does anything, so that it never reaches
 * the screen under a sheet. Not with Alt held: AltGr arrives as Ctrl + Alt on Windows and the web, and AltGr + S types a
 * character on some layouts. On the web the browser's own "Save page as" is kept from the key by the shell, since a key
 * pressed in the hidden input of a text field reaches Compose only after the browser has acted on it.
 */
internal fun Modifier.saveShortcut(onSave: () -> Unit) = onPreviewKeyEvent { keyEvent ->
    val isSave = keyEvent.key == Key.S && (keyEvent.isCtrlPressed || keyEvent.isMetaPressed) && !keyEvent.isAltPressed
    // The key-up is taken too, so that nothing under this sees half a shortcut.
    if (isSave && keyEvent.type == KeyEventType.KeyDown) onSave()
    isSave
}
