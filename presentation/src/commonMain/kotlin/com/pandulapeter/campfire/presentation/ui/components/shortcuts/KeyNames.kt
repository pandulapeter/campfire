/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components.shortcuts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.input.key.Key
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.key_ctrl
import com.pandulapeter.campfire.presentation.resources.key_shift
import com.pandulapeter.campfire.presentation.resources.key_space

/** The names of the keys a hint writes as words, in the app's language (German writes Ctrl as Strg). */
@Immutable
internal class KeyNames(
    val ctrl: String,
    val shift: String,
    val space: String,
) {
    fun of(key: Key) = when (key) {
        Key.Spacebar -> space
        else -> error("$key has no name; give its chord a label.")
    }
}

@Composable
internal fun keyNames() = KeyNames(
    ctrl = stringResource(Res.string.key_ctrl),
    shift = stringResource(Res.string.key_shift),
    space = stringResource(Res.string.key_space),
)

/**
 * How [this] is written for the person at the keyboard: Apple's symbols run together where [isApple] (`⇧⌘Z`), the
 * modifiers' names joined by plus signs elsewhere (`Ctrl+Shift+Z`), in the order each platform's own menus use.
 */
internal fun KeyChord.label(isApple: Boolean, names: KeyNames): String {
    val keyName = label ?: names.of(key)
    val isShifted = shift == KeyChord.Shift.REQUIRED
    return if (isApple) {
        buildString {
            if (isShifted) append(APPLE_SHIFT)
            if (isCommand) append(APPLE_COMMAND)
            append(keyName)
        }
    } else {
        listOfNotNull(names.ctrl.takeIf { isCommand }, names.shift.takeIf { isShifted }, keyName).joinToString(separator = "+")
    }
}

private const val APPLE_SHIFT = "⇧"
private const val APPLE_COMMAND = "⌘"
