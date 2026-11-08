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

import androidx.compose.ui.input.key.Key
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KeyChordTest {

    private val english = KeyNames(ctrl = "Ctrl", shift = "Shift", space = "Space", enter = "Enter")
    private val german = KeyNames(ctrl = "Strg", shift = "Umschalt", space = "Leertaste", enter = "Eingabe")

    @Test
    fun `a chord is written with the modifiers' names and plus signs, in the language's words`() {
        assertEquals("Ctrl+S", KeyShortcut.SAVE.chord.label(isApple = false, names = english))
        assertEquals("Strg+S", KeyShortcut.SAVE.chord.label(isApple = false, names = german))
        assertEquals("Ctrl++", KeyShortcut.ZOOM_IN.chord.label(isApple = false, names = english))
        assertEquals("Leertaste", KeyShortcut.PLAY.chord.label(isApple = false, names = german))
        assertEquals("M", KeyShortcut.METRONOME.chord.label(isApple = false, names = english))
    }

    @Test
    fun `on an Apple keyboard a chord is written in symbols, Shift before Command`() {
        assertEquals("⌘S", KeyShortcut.SAVE.chord.label(isApple = true, names = english))
        val redo = KeyChord.command(Key.Z, "Z", shift = KeyChord.Shift.REQUIRED)
        assertEquals("⇧⌘Z", redo.label(isApple = true, names = english))
        assertEquals("Ctrl+Shift+Z", redo.label(isApple = false, names = english))
        assertEquals("Space", KeyShortcut.PLAY.chord.label(isApple = true, names = english))
    }

    @Test
    fun `Shift is ignored, refused, or required as the chord says, and Alt is always refused`() {
        assertTrue(KeyShortcut.SAVE.chord.matches(Key.S, isCtrlOrMeta = true, isShift = true, isAlt = false))
        assertFalse(KeyShortcut.ITALIC.chord.matches(Key.I, isCtrlOrMeta = true, isShift = true, isAlt = false))
        assertTrue(KeyShortcut.ITALIC.chord.matches(Key.I, isCtrlOrMeta = true, isShift = false, isAlt = false))
        val redo = KeyChord.command(Key.Z, "Z", shift = KeyChord.Shift.REQUIRED)
        assertFalse(redo.matches(Key.Z, isCtrlOrMeta = true, isShift = false, isAlt = false))
        KeyShortcut.entries.forEach { shortcut ->
            assertFalse(shortcut.chord.matches(shortcut.chord.key, isCtrlOrMeta = shortcut.chord.isCommand, isShift = false, isAlt = true), shortcut.name)
        }
    }

    @Test
    fun `no two shortcuts share their keys, Shift telling apart only the ones that look at it`() {
        KeyShortcut.entries.forEach { first ->
            KeyShortcut.entries.filter { it.ordinal > first.ordinal }.forEach { second ->
                val sharedKeys = (first.chord.alternates + first.chord.key).intersect(second.chord.alternates + second.chord.key)
                val isShiftShared = listOf(true, false).any { isShift ->
                    sharedKeys.any { key ->
                        first.chord.matches(key, isCtrlOrMeta = first.chord.isCommand, isShift = isShift, isAlt = false) &&
                            second.chord.matches(key, isCtrlOrMeta = first.chord.isCommand, isShift = isShift, isAlt = false)
                    }
                }
                assertFalse(isShiftShared, "${first.name} and ${second.name}")
            }
        }
    }

    @Test
    fun `undo, redo, and the editor search's steps are told apart by Shift`() {
        assertTrue(KeyShortcut.UNDO.chord.matches(Key.Z, isCtrlOrMeta = true, isShift = false, isAlt = false))
        assertFalse(KeyShortcut.UNDO.chord.matches(Key.Z, isCtrlOrMeta = true, isShift = true, isAlt = false))
        assertTrue(KeyShortcut.REDO.chord.matches(Key.Z, isCtrlOrMeta = true, isShift = true, isAlt = false))
        assertTrue(KeyShortcut.FIND_PREVIOUS.chord.matches(Key.F3, isCtrlOrMeta = false, isShift = true, isAlt = false))
        assertFalse(KeyShortcut.FIND_NEXT.chord.matches(Key.F3, isCtrlOrMeta = false, isShift = true, isAlt = false))
        assertTrue(KeyShortcut.FIND_NEXT_FROM_QUERY.chord.matches(Key.NumPadEnter, isCtrlOrMeta = false, isShift = false, isAlt = false))
    }

    @Test
    fun `the new chords are written with their keys' names`() {
        assertEquals("Ctrl+Shift+↑", KeyShortcut.TRANSPOSE_UP.chord.label(isApple = false, names = english))
        assertEquals("⇧⌘↓", KeyShortcut.TRANSPOSE_DOWN.chord.label(isApple = true, names = english))
        assertEquals("Shift+F3", KeyShortcut.FIND_PREVIOUS.chord.label(isApple = false, names = english))
        assertEquals("Umschalt+Eingabe", KeyShortcut.FIND_PREVIOUS_FROM_QUERY.chord.label(isApple = false, names = german))
        assertEquals("⌘,", KeyShortcut.SETTINGS.chord.label(isApple = true, names = english))
    }
}
