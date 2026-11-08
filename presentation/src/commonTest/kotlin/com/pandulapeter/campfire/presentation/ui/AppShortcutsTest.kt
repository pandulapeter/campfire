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
    fun `M toggles the metronome only without Ctrl, Cmd or Alt, and Space is no app-wide shortcut`() {
        assertEquals(AppShortcut.ToggleMetronome, shortcut(Key.M))
        assertNull(shortcut(Key.M, isCtrlOrMeta = true))
        assertNull(shortcut(Key.M, isAlt = true))
        assertNull(shortcut(Key.Spacebar))
    }

    @Test
    fun `Ctrl or Cmd and V pastes a song, and the shortcuts of the screens are none of the app's`() {
        assertEquals(AppShortcut.PasteSong, shortcut(Key.V, isCtrlOrMeta = true))
        listOf(Key.E, Key.R, Key.B, Key.Z, Key.Y).forEach { key -> assertNull(shortcut(key, isCtrlOrMeta = true)) }
        assertNull(shortcut(Key.DirectionUp, isCtrlOrMeta = true, isShift = true))
        assertNull(shortcut(Key.F3))
    }

    @Test
    fun `T taps the tempo like M, and Ctrl or Cmd with N, A, or S are the list screens' New, Select all, and Share or export`() {
        assertEquals(AppShortcut.TapTempo, shortcut(Key.T))
        assertNull(shortcut(Key.T, isCtrlOrMeta = true))
        assertEquals(AppShortcut.NewItem, shortcut(Key.N, isCtrlOrMeta = true))
        assertNull(shortcut(Key.N, isCtrlOrMeta = true, isShift = true))
        assertEquals(AppShortcut.SelectAllSongs, shortcut(Key.A, isCtrlOrMeta = true))
        assertNull(shortcut(Key.A, isCtrlOrMeta = true, isShift = true))
        assertEquals(AppShortcut.ExportSetlist, shortcut(Key.S, isCtrlOrMeta = true))
    }

    @Test
    fun `Ctrl or Cmd and a digit from 1 to 6 or comma select a main screen, from either row of digits`() {
        assertEquals(AppShortcut.SelectScreen(KeyShortcut.SCREEN_1), shortcut(Key.One, isCtrlOrMeta = true))
        assertEquals(AppShortcut.SelectScreen(KeyShortcut.SCREEN_6), shortcut(Key.NumPad6, isCtrlOrMeta = true))
        assertEquals(AppShortcut.SelectScreen(KeyShortcut.SETTINGS), shortcut(Key.Comma, isCtrlOrMeta = true))
        assertNull(shortcut(Key.Seven, isCtrlOrMeta = true))
        assertNull(shortcut(Key.One))
    }

    @Test
    fun `Ctrl or Cmd and slash, and a question mark, open the shortcuts sheet, while a screen's own keys are no app-wide shortcut`() {
        assertEquals(AppShortcut.ShowKeyboardShortcuts(KeyShortcut.SHORTCUTS), shortcut(Key.Slash, isCtrlOrMeta = true))
        assertEquals(AppShortcut.ShowKeyboardShortcuts(KeyShortcut.SHORTCUTS_QUESTION_MARK), shortcut(Key.Slash, isShift = true))
        assertNull(shortcut(Key.Slash))
        assertNull(shortcut(Key.PageDown))
        assertNull(shortcut(Key.DirectionLeft))
        assertNull(shortcut(Key.Escape))
    }

    private fun shortcut(key: Key, isCtrlOrMeta: Boolean = false, isShift: Boolean = false, isAlt: Boolean = false) =
        appShortcutOf(key, isCtrlOrMeta = isCtrlOrMeta, isShift = isShift, isAlt = isAlt)
}
