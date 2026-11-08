/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songEditor.search

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import com.pandulapeter.campfire.presentation.ui.components.shortcuts.KeyShortcut

/**
 * Answers Ctrl / Cmd + F with [onFind] and Ctrl / Cmd + R with [onReplace], on the editor and on its search sheet alike,
 * so the key opens either sheet from the text and switches between them from inside one. Like `saveShortcut` it hears
 * the key only along the focus path, and not with Alt held, since AltGr arrives as Ctrl + Alt on Windows and the web and
 * types a character with these keys on some layouts. The key-up is taken too, so that nothing under this sees half a
 * shortcut. On the web the browser's own find bar and reload are kept from these keys by the shell, while the editor is
 * on top, since a key pressed in the hidden input of a text field reaches Compose only after the browser has acted on it.
 *
 * F3 and Shift + F3 step to the next and the previous match ([onFindNext], [onFindPrevious]) from anywhere in the sheet;
 * from the editor, where there is no match to step to, they open Find, as they do in the browser and the code editors
 * whose key it is. The shell keeps the browser's own from F3 on the web.
 */
internal fun Modifier.editorSearchShortcuts(
    onFind: () -> Unit,
    onReplace: () -> Unit,
    onFindNext: () -> Unit = onFind,
    onFindPrevious: () -> Unit = onFind,
) = onPreviewKeyEvent { keyEvent ->
    val action = when {
        KeyShortcut.FIND.matches(keyEvent) -> onFind
        KeyShortcut.REPLACE.matches(keyEvent) -> onReplace
        KeyShortcut.FIND_NEXT.matches(keyEvent) -> onFindNext
        KeyShortcut.FIND_PREVIOUS.matches(keyEvent) -> onFindPrevious
        else -> null
    }
    if (action != null && keyEvent.type == KeyEventType.KeyDown) action()
    action != null
}

/**
 * Enter and Shift + Enter in the search's query step to the next and the previous match, as a search field is finished
 * with Enter, whether the sheet replaces or not; the replacement's Enter is its own. A soft keyboard's Search or Next
 * key arrives as the field's keyboard action instead, which is left as it is.
 */
internal fun Modifier.editorQueryShortcuts(onFindNext: () -> Unit, onFindPrevious: () -> Unit) = onPreviewKeyEvent { keyEvent ->
    val action = when {
        KeyShortcut.FIND_NEXT_FROM_QUERY.matches(keyEvent) -> onFindNext
        KeyShortcut.FIND_PREVIOUS_FROM_QUERY.matches(keyEvent) -> onFindPrevious
        else -> null
    }
    if (action != null && keyEvent.type == KeyEventType.KeyDown) action()
    action != null
}
