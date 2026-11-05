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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppShortcutsTest {

    @Test
    fun `Ctrl or Cmd and F opens the search, never with Alt or alone`() {
        assertEquals(AppShortcut.OpenSearch, shortcut(Key.F, isCtrlOrMeta = true))
        assertEquals(AppShortcut.OpenSearch, shortcut(Key.F, isCtrlOrMeta = true, isShift = true))
        assertNull(shortcut(Key.F, isCtrlOrMeta = true, isAlt = true))
        assertNull(shortcut(Key.F))
    }

    @Test
    fun `Ctrl or Cmd and plus, minus or zero zoom the song text, never with Alt or alone`() {
        listOf(Key.Equals, Key.Plus, Key.NumPadAdd).forEach { key -> assertEquals(AppShortcut.ZoomSongText(1), shortcut(key, isCtrlOrMeta = true)) }
        // The plus of a US layout is Shift and equals.
        assertEquals(AppShortcut.ZoomSongText(1), shortcut(Key.Equals, isCtrlOrMeta = true, isShift = true))
        assertEquals(AppShortcut.ZoomSongText(-1), shortcut(Key.Minus, isCtrlOrMeta = true))
        assertEquals(AppShortcut.ZoomSongText(null), shortcut(Key.Zero, isCtrlOrMeta = true))
        listOf(Key.Equals, Key.Minus, Key.Zero).forEach { key ->
            assertNull(shortcut(key, isCtrlOrMeta = true, isAlt = true))
            assertNull(shortcut(key))
        }
    }

    @Test
    fun `Space and M toggle the metronome only without Ctrl, Cmd or Alt`() {
        assertEquals(AppShortcut.ToggleMetronome(isSpace = true), shortcut(Key.Spacebar))
        assertEquals(AppShortcut.ToggleMetronome(isSpace = false), shortcut(Key.M))
        listOf(Key.Spacebar, Key.M).forEach { key ->
            assertNull(shortcut(key, isCtrlOrMeta = true))
            assertNull(shortcut(key, isAlt = true))
        }
    }

    @Test
    fun `Ctrl or Cmd and V pastes a song, and the shortcuts of the screens are none of the app's`() {
        assertEquals(AppShortcut.PasteSong, shortcut(Key.V, isCtrlOrMeta = true))
        listOf(Key.S, Key.E, Key.R, Key.B).forEach { key -> assertNull(shortcut(key, isCtrlOrMeta = true)) }
    }

    private fun shortcut(key: Key, isCtrlOrMeta: Boolean = false, isShift: Boolean = false, isAlt: Boolean = false) =
        appShortcutOf(key, isCtrlOrMeta = isCtrlOrMeta, isShift = isShift, isAlt = isAlt)
}
