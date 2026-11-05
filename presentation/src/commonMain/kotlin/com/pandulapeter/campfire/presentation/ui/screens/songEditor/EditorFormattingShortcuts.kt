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
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import com.pandulapeter.campfire.chordpro.model.ChordProLine.Lyrics.Formatting
import com.pandulapeter.campfire.presentation.ui.components.shortcuts.KeyShortcut

/**
 * Answers Ctrl / Cmd + B, I, and U with [onToggle] and the style they stand for everywhere, while [isEnabled] (the
 * field is shown, so that a key pressed over the preview alone never edits text nobody sees). Like
 * `editorSearchShortcuts` it hears the key only along the focus path and not with Alt held, since AltGr arrives as
 * Ctrl + Alt on Windows and the web and types a character with these keys on some layouts, and takes the key-up too, so
 * that nothing under this sees half a shortcut. On the web the browser's own bookmarks, page info, and page source are
 * kept from these keys by the shell while the editor is on top.
 */
internal fun Modifier.editorFormattingShortcuts(isEnabled: Boolean, onToggle: (Formatting.Style) -> Unit) = onPreviewKeyEvent { keyEvent ->
    val style = if (isEnabled) Formatting.Style.entries.firstOrNull { it.keyShortcut.matches(keyEvent) } else null
    if (style != null && keyEvent.type == KeyEventType.KeyDown) onToggle(style)
    style != null
}

/** The shortcut that toggles this style, on the toolbar's buttons as well. */
internal val Formatting.Style.keyShortcut
    get() = when (this) {
        Formatting.Style.BOLD -> KeyShortcut.BOLD
        Formatting.Style.ITALIC -> KeyShortcut.ITALIC
        Formatting.Style.UNDERLINE -> KeyShortcut.UNDERLINE
    }
