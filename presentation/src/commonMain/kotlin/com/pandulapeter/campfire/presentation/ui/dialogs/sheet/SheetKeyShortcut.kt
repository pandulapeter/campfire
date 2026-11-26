/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs.sheet

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import com.pandulapeter.campfire.presentation.ui.components.shortcuts.KeyShortcut

/**
 * Hears [shortcut]'s key over the sheet, where the app's root does not (the sheet holds the focus), and does what it
 * says, as the sheet's own button would. Heard after what is focused inside the sheet, as [closeShortcut] is, so that a
 * field or a stepper's value being typed into takes its letter first.
 */
internal fun Modifier.keyShortcut(shortcut: Pair<KeyShortcut, () -> Unit>?) = if (shortcut == null) {
    this
} else {
    onKeyEvent { keyEvent ->
        val isShortcut = shortcut.first.matches(keyEvent)
        if (isShortcut && keyEvent.type == KeyEventType.KeyDown) shortcut.second()
        isShortcut
    }
}
