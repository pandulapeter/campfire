/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.unit.LayoutDirection
import com.pandulapeter.campfire.presentation.ui.components.horizontalArrowFor
import com.pandulapeter.campfire.presentation.ui.components.shortcuts.KeyShortcut

/**
 * The arrow a control stepping [step] along a song read as a strip names, the one the keyboard handler steps it by
 * (`SongKeyboardShortcuts`, through `horizontalArrowStep`): in a strip read from the right ([readsLeftwards]), Right goes
 * back and Left forward.
 */
internal fun songArrowShortcut(step: Int, readsLeftwards: Boolean) =
    if (horizontalArrowFor(step, if (readsLeftwards) LayoutDirection.Rtl else LayoutDirection.Ltr) == Key.DirectionLeft) {
        KeyShortcut.SONG_LEFT
    } else {
        KeyShortcut.SONG_RIGHT
    }

/** The Shift arrow a control switching [step] songs in a pager laid out in [direction] names, as [songArrowShortcut]. */
internal fun songSwitchArrowShortcut(step: Int, direction: LayoutDirection) =
    if (horizontalArrowFor(step, direction) == Key.DirectionLeft) KeyShortcut.SONG_SWITCH_LEFT else KeyShortcut.SONG_SWITCH_RIGHT
