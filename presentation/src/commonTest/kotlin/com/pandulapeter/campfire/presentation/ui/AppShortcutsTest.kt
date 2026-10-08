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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppShortcutsTest {

    @Test
    fun `Ctrl or Cmd and F opens the search, never with Alt or alone`() {
        assertEquals(AppShortcut.OpenSearch, appShortcutOf(ShortcutKey.F, isCtrlOrMeta = true, isAlt = false))
        assertNull(appShortcutOf(ShortcutKey.F, isCtrlOrMeta = true, isAlt = true))
        assertNull(appShortcutOf(ShortcutKey.F, isCtrlOrMeta = false, isAlt = false))
    }

    @Test
    fun `Ctrl or Cmd and plus, minus or zero zoom the song text, never with Alt or alone`() {
        assertEquals(AppShortcut.ZoomSongText(1), appShortcutOf(ShortcutKey.PLUS, isCtrlOrMeta = true, isAlt = false))
        assertEquals(AppShortcut.ZoomSongText(-1), appShortcutOf(ShortcutKey.MINUS, isCtrlOrMeta = true, isAlt = false))
        assertEquals(AppShortcut.ZoomSongText(null), appShortcutOf(ShortcutKey.ZERO, isCtrlOrMeta = true, isAlt = false))
        listOf(ShortcutKey.PLUS, ShortcutKey.MINUS, ShortcutKey.ZERO).forEach { key ->
            assertNull(appShortcutOf(key, isCtrlOrMeta = true, isAlt = true))
            assertNull(appShortcutOf(key, isCtrlOrMeta = false, isAlt = false))
        }
    }

    @Test
    fun `Space and M toggle the metronome only without Ctrl, Cmd or Alt`() {
        assertEquals(AppShortcut.ToggleMetronome(isSpace = true), appShortcutOf(ShortcutKey.SPACE, isCtrlOrMeta = false, isAlt = false))
        assertEquals(AppShortcut.ToggleMetronome(isSpace = false), appShortcutOf(ShortcutKey.M, isCtrlOrMeta = false, isAlt = false))
        listOf(ShortcutKey.SPACE, ShortcutKey.M).forEach { key ->
            assertNull(appShortcutOf(key, isCtrlOrMeta = true, isAlt = false))
            assertNull(appShortcutOf(key, isCtrlOrMeta = false, isAlt = true))
        }
    }
}
