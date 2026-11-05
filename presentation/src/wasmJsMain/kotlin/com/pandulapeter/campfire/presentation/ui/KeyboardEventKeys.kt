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

import androidx.compose.ui.input.key.Key
import com.pandulapeter.campfire.presentation.ui.components.shortcuts.KeyShortcut
import org.w3c.dom.events.KeyboardEvent

/**
 * Whether [this] is [shortcut]'s chord, for the shell's listeners that hear a key before Compose does. Only for the
 * shortcuts on a letter: the key is the physical one as Compose goes by it (`code`), else the character a virtual
 * keyboard sends, which has no code.
 */
internal fun KeyboardEvent.isShortcut(shortcut: KeyShortcut): Boolean {
    val letter = code.removePrefix("Key").takeIf { code.startsWith("Key") && it.length == 1 } ?: key.takeIf { it.length == 1 } ?: return false
    val composeKey = LETTER_KEYS[letter.uppercase().single()] ?: return false
    return shortcut.chord.matches(key = composeKey, isCtrlOrMeta = ctrlKey || metaKey, isShift = shiftKey, isAlt = altKey)
}

private val LETTER_KEYS: Map<Char, Key> = listOf(
    Key.A, Key.B, Key.C, Key.D, Key.E, Key.F, Key.G, Key.H, Key.I, Key.J, Key.K, Key.L, Key.M,
    Key.N, Key.O, Key.P, Key.Q, Key.R, Key.S, Key.T, Key.U, Key.V, Key.W, Key.X, Key.Y, Key.Z,
).mapIndexed { index, key -> 'A' + index to key }.toMap()
