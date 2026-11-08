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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.key.Key
import com.pandulapeter.campfire.presentation.ui.components.shortcuts.KeyChord
import com.pandulapeter.campfire.presentation.ui.components.shortcuts.systemShortcutTexts
import com.pandulapeter.campfire.presentation.ui.dialogs.shortcuts.shortcutFeatures
import platform.UIKit.UIKeyInputDownArrow
import platform.UIKit.UIKeyInputEnd
import platform.UIKit.UIKeyInputEscape
import platform.UIKit.UIKeyInputHome
import platform.UIKit.UIKeyInputLeftArrow
import platform.UIKit.UIKeyInputPageDown
import platform.UIKit.UIKeyInputPageUp
import platform.UIKit.UIKeyInputRightArrow
import platform.UIKit.UIKeyInputUpArrow
import platform.UIKit.UIKeyModifierCommand
import platform.UIKit.UIKeyModifierFlags
import platform.UIKit.UIKeyModifierShift

/**
 * One shortcut as the app module's `UIKeyCommand` names it in the Command-hold overlay: [title] in the app's language,
 * [input] and [modifierFlags] the keys, as UIKit writes them.
 */
class IosKeyCommand internal constructor(
    val title: String,
    val input: String,
    val modifierFlags: UIKeyModifierFlags,
)

/**
 * Hands the shortcuts that work here to [onChanged] as [IosKeyCommand]s, in the app's language, again whenever the
 * language or a feature they depend on changes. A chord whose key UIKit has no input for is left out.
 */
@Composable
internal fun IosKeyCommandsEffect(
    viewModel: CampfireViewModel,
    onChanged: (List<IosKeyCommand>) -> Unit,
) {
    val groups = systemShortcutTexts(shortcutFeatures(viewModel.preferencesController))
    val latestOnChanged by rememberUpdatedState(onChanged)
    LaunchedEffect(groups) {
        latestOnChanged(
            groups.flatMap { group ->
                group.shortcuts.mapNotNull { shortcut ->
                    shortcut.chord.input?.let { input -> IosKeyCommand(title = shortcut.description, input = input, modifierFlags = shortcut.chord.modifierFlags) }
                }
            },
        )
    }
}

/** The key as UIKit names it: its own constant for a key with a name, the character typed for the rest. */
private val KeyChord.input: String?
    get() = when (key) {
        Key.DirectionUp -> UIKeyInputUpArrow
        Key.DirectionDown -> UIKeyInputDownArrow
        Key.DirectionLeft -> UIKeyInputLeftArrow
        Key.DirectionRight -> UIKeyInputRightArrow
        Key.PageUp -> UIKeyInputPageUp
        Key.PageDown -> UIKeyInputPageDown
        Key.MoveHome -> UIKeyInputHome
        Key.MoveEnd -> UIKeyInputEnd
        Key.Escape -> UIKeyInputEscape
        Key.Spacebar -> " "
        Key.Enter -> "\r"
        Key.F3 -> null
        else -> label?.lowercase()
    }

/** Cmd for Ctrl / Cmd, and Shift where it is held but not already the character ([KeyChord.Shift.IN_LABEL], `?`). */
private val KeyChord.modifierFlags: UIKeyModifierFlags
    get() = (if (isCommand) UIKeyModifierCommand else 0L) or (if (shift == KeyChord.Shift.REQUIRED) UIKeyModifierShift else 0L)
