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
 * Puts the sheet away with [shortcut], the key that opened it (U for the tuner), so that a second press closes what the
 * first opened. Heard after what is focused inside the sheet rather than before it, so that a field or a stepper's value
 * being typed into takes its letter first.
 */
internal fun Modifier.closeShortcut(shortcut: KeyShortcut?, onClose: () -> Unit) = if (shortcut == null) {
    this
} else {
    onKeyEvent { keyEvent ->
        val isClose = shortcut.matches(keyEvent)
        if (isClose && keyEvent.type == KeyEventType.KeyDown) {
            println("Keyboard: sheet closed by its shortcut")
            onClose()
        }
        isClose
    }
}
