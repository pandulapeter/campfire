/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
@file:OptIn(ExperimentalWasmJsInterop::class)

package com.pandulapeter.campfire.presentation.ui

import androidx.compose.ui.input.key.Key
import com.pandulapeter.campfire.presentation.ui.components.shortcuts.KeyShortcut
import org.w3c.dom.events.KeyboardEvent
import kotlin.js.ExperimentalWasmJsInterop

/**
 * Whether [this] is [shortcut]'s chord, where it works on the web, for the shell's listeners that hear a key before
 * Compose does. Only for the shortcuts on a letter, a digit, the comma, the slash, or F3: the key is the physical one as Compose
 * goes by it (`code`), else for a letter the character a virtual keyboard sends, which has no code.
 */
internal fun KeyboardEvent.isShortcut(shortcut: KeyShortcut): Boolean {
    val composeKey = CODE_KEYS[code] ?: letterKey() ?: return false
    return shortcut.isAvailable && shortcut.chord.matches(key = composeKey, isCtrlOrMeta = ctrlKey || metaKey, isShift = shiftKey, isAlt = altKey)
}

private fun KeyboardEvent.letterKey(): Key? {
    val letter = code.removePrefix("Key").takeIf { code.startsWith("Key") && it.length == 1 } ?: key.takeIf { it.length == 1 } ?: return null
    return LETTER_KEYS[letter.uppercase().single()]
}

/** Whether [event] is typed into a field: the hidden input of a focused Compose field, or anything else editable. */
internal fun isTypingTarget(event: KeyboardEvent): Boolean =
    js("event.target && (event.target.tagName === 'INPUT' || event.target.tagName === 'TEXTAREA' || event.target.isContentEditable === true)")

private val LETTER_KEYS: Map<Char, Key> = listOf(
    Key.A, Key.B, Key.C, Key.D, Key.E, Key.F, Key.G, Key.H, Key.I, Key.J, Key.K, Key.L, Key.M,
    Key.N, Key.O, Key.P, Key.Q, Key.R, Key.S, Key.T, Key.U, Key.V, Key.W, Key.X, Key.Y, Key.Z,
).mapIndexed { index, key -> 'A' + index to key }.toMap()

private val CODE_KEYS: Map<String, Key> = listOf(Key.Zero, Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine)
    .flatMapIndexed { digit, key -> listOf("Digit$digit" to key, "Numpad$digit" to key) }
    .toMap() + mapOf("Comma" to Key.Comma, "Slash" to Key.Slash, "NumpadDivide" to Key.NumPadDivide, "F3" to Key.F3)
