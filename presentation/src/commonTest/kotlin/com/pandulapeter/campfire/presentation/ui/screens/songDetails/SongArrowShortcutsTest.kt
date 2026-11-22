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

import androidx.compose.ui.unit.LayoutDirection
import com.pandulapeter.campfire.presentation.ui.components.horizontalArrowStep
import com.pandulapeter.campfire.presentation.ui.components.shortcuts.KeyShortcut
import kotlin.test.Test
import kotlin.test.assertEquals

class SongArrowShortcutsTest {

    @Test
    fun `a strip read from the left steps back by Left and forward by Right`() {
        assertEquals(KeyShortcut.SONG_LEFT, songArrowShortcut(-1, readsLeftwards = false))
        assertEquals(KeyShortcut.SONG_RIGHT, songArrowShortcut(1, readsLeftwards = false))
    }

    @Test
    fun `a strip read from the right steps back by Right and forward by Left`() {
        assertEquals(KeyShortcut.SONG_RIGHT, songArrowShortcut(-1, readsLeftwards = true))
        assertEquals(KeyShortcut.SONG_LEFT, songArrowShortcut(1, readsLeftwards = true))
    }

    @Test
    fun `a right-to-left pager switches back by Shift Right`() {
        assertEquals(KeyShortcut.SONG_SWITCH_RIGHT, songSwitchArrowShortcut(-1, LayoutDirection.Rtl))
        assertEquals(KeyShortcut.SONG_SWITCH_LEFT, songSwitchArrowShortcut(1, LayoutDirection.Rtl))
        assertEquals(KeyShortcut.SONG_SWITCH_LEFT, songSwitchArrowShortcut(-1, LayoutDirection.Ltr))
    }

    @Test
    fun `every arrow a control names is read by the keyboard handler as its step`() = listOf(false, true).forEach { readsLeftwards ->
        val direction = if (readsLeftwards) LayoutDirection.Rtl else LayoutDirection.Ltr
        listOf(-1, 1).forEach { step ->
            assertEquals(step, songArrowShortcut(step, readsLeftwards).chord.key.horizontalArrowStep(direction))
            assertEquals(step, songSwitchArrowShortcut(step, direction).chord.key.horizontalArrowStep(direction))
        }
    }
}
