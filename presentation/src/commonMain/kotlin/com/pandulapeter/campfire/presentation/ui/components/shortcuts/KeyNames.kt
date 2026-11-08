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
import com.pandulapeter.campfire.presentation.resources.key_end
import com.pandulapeter.campfire.presentation.resources.key_enter
import com.pandulapeter.campfire.presentation.resources.key_escape
import com.pandulapeter.campfire.presentation.resources.key_home
import com.pandulapeter.campfire.presentation.resources.key_page_down
import com.pandulapeter.campfire.presentation.resources.key_page_up
import com.pandulapeter.campfire.presentation.resources.key_scroll
import com.pandulapeter.campfire.presentation.resources.key_shift
import com.pandulapeter.campfire.presentation.resources.key_space

/**
 * The names of the keys a hint writes as words, in the app's language (German writes Ctrl as Strg), and of the scroll
 * wheel turned with Ctrl / Cmd held, which the shortcuts sheet lists beside the keys.
 */
@Immutable
internal class KeyNames(
    val ctrl: String,
    val shift: String,
    val space: String,
    val enter: String,
    val escape: String,
    val pageUp: String,
    val pageDown: String,
    val home: String,
    val end: String,
    val scroll: String,
) {
    fun of(key: Key) = when (key) {
        Key.Spacebar -> space
        Key.Enter -> enter
        Key.Escape -> escape
        Key.PageUp -> pageUp
        Key.PageDown -> pageDown
        Key.MoveHome -> home
        Key.MoveEnd -> end
        else -> error("$key has no name; give its chord a label.")
    }
}

@Composable
internal fun keyNames() = KeyNames(
    ctrl = stringResource(Res.string.key_ctrl),
    shift = stringResource(Res.string.key_shift),
    space = stringResource(Res.string.key_space),
    enter = stringResource(Res.string.key_enter),
    escape = stringResource(Res.string.key_escape),
    pageUp = stringResource(Res.string.key_page_up),
    pageDown = stringResource(Res.string.key_page_down),
    home = stringResource(Res.string.key_home),
    end = stringResource(Res.string.key_end),
    scroll = stringResource(Res.string.key_scroll),
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

/** Ctrl / Cmd held while the scroll wheel turns (`⌘ scroll`, `Ctrl+scroll`), written as [label] writes a chord. */
internal fun KeyNames.commandScrollLabel(isApple: Boolean) = if (isApple) "$APPLE_COMMAND $scroll" else "$ctrl+$scroll"

private const val APPLE_SHIFT = "⇧"
private const val APPLE_COMMAND = "⌘"
