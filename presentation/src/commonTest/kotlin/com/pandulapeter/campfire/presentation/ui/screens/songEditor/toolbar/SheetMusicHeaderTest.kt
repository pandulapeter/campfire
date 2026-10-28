/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songEditor.toolbar

import kotlin.test.Test
import kotlin.test.assertEquals

class SheetMusicHeaderTest {

    @Test
    fun `no key and no time give a tune in C with eighth notes`() {
        assertEquals(listOf("X:1", "L:1/8", "K:C"), sheetMusicHeader(key = null, time = null))
    }

    @Test
    fun `a key with an accidental keeps it`() {
        assertEquals("K:Bb", keyLineOf("Bb"))
        assertEquals("K:F#m", keyLineOf("F#m"))
    }

    @Test
    fun `a minor key is minor however it is written`() {
        assertEquals("K:Am", keyLineOf("a-moll"))
        assertEquals("K:Am", keyLineOf("A minor"))
        assertEquals("K:Am", keyLineOf("Am7"))
        assertEquals("K:Dm", keyLineOf("Dm (capo 2)"))
    }

    @Test
    fun `a mode is written as its first three letters`() {
        assertEquals("K:D dor", keyLineOf("D dorian"))
        assertEquals("K:D mix", keyLineOf("D Mixolydian"))
    }

    @Test
    fun `a major key is its root alone`() {
        assertEquals("K:C", keyLineOf("Cmaj7"))
        assertEquals("K:G", keyLineOf("G-dur"))
        assertEquals("K:G", keyLineOf("G mayor"))
    }

    @Test
    fun `a key that names no note gives C`() {
        assertEquals("K:C", keyLineOf("S"))
    }

    @Test
    fun `a time adds the meter after the reference number`() {
        assertEquals(listOf("X:1", "M:6/8", "L:1/8", "K:C"), sheetMusicHeader(key = null, time = "6/8"))
        assertEquals(listOf("X:1", "M:6/8", "L:1/8", "K:C"), sheetMusicHeader(key = null, time = "6 / 8"))
        assertEquals("M:C", sheetMusicHeader(key = null, time = "C")[1])
        assertEquals("M:C|", sheetMusicHeader(key = null, time = "¢")[1])
    }

    @Test
    fun `a time that is no signature adds no meter`() {
        assertEquals(listOf("X:1", "L:1/8", "K:C"), sheetMusicHeader(key = null, time = "5/3"))
        assertEquals(listOf("X:1", "L:1/8", "K:C"), sheetMusicHeader(key = null, time = "waltz"))
    }

    private fun keyLineOf(key: String) = sheetMusicHeader(key = key, time = null).last()
}
