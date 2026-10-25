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
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import com.pandulapeter.campfire.chordpro.model.ChordProLine.Lyrics.Formatting

/**
 * Answers Ctrl / Cmd + B, I, and U with [onToggle] and the style they stand for everywhere, while [isEnabled] (the
 * field is shown, so that a key pressed over the preview alone never edits text nobody sees). Like
 * `editorSearchShortcuts` it hears the key only along the focus path and not with Alt held, since AltGr arrives as
 * Ctrl + Alt on Windows and the web and types a character with these keys on some layouts, and takes the key-up too, so
 * that nothing under this sees half a shortcut. On the web the browser's own bookmarks, page info, and page source are
 * kept from these keys by the shell while the editor is on top.
 */
internal fun Modifier.editorFormattingShortcuts(isEnabled: Boolean, onToggle: (Formatting.Style) -> Unit) = onPreviewKeyEvent { keyEvent ->
    val isShortcut = isEnabled && (keyEvent.isCtrlPressed || keyEvent.isMetaPressed) && !keyEvent.isAltPressed && !keyEvent.isShiftPressed
    val style = when {
        !isShortcut -> null
        keyEvent.key == Key.B -> Formatting.Style.BOLD
        keyEvent.key == Key.I -> Formatting.Style.ITALIC
        keyEvent.key == Key.U -> Formatting.Style.UNDERLINE
        else -> null
    }
    if (style != null && keyEvent.type == KeyEventType.KeyDown) onToggle(style)
    style != null
}
