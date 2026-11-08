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
import androidx.compose.runtime.compositionLocalOf
import com.pandulapeter.campfire.presentation.ui.platform.isAppleKeyboard

/**
 * Whether the shortcuts' hints are shown, provided once at the root: only where there is a keyboard to press them on
 * (`rememberHasHardwareKeyboard`), since on a phone held in the hand a tooltip saying Ctrl+F is noise.
 */
internal val LocalShowsShortcutHints = compositionLocalOf { false }

/**
 * How [this] is written in a hint here (`Ctrl+F`, `⌘F`), or null where hints are not shown or the shortcut does not
 * work on this platform ([KeyShortcut.isAvailable]).
 */
@Composable
internal fun KeyShortcut.hint(): String? =
    if (LocalShowsShortcutHints.current && isAvailable) chord.label(isApple = isAppleKeyboard, names = keyNames()) else null
