/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.notation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotationPlayedLinesTest {

    @Test
    fun `a double bar is one bar line`() {
        assertEquals(2, barCountOf(staff(10f, 50f, 53f), firstMusicX = 5f))
    }

    @Test
    fun `a bar line opening a system closes no bar`() {
        val strokes = staff(63.9f, 68.4f, 438.3f, 792.0f, 796.5f)
        assertEquals(2, barCountOf(strokes, firstMusicX = 81.4f))
        assertEquals(3, barCountOf(strokes, firstMusicX = 29f))
    }

    @Test
    fun `staves whose bar lines fall apart count as the staff playing the most bars`() {
        val piano = staff(66f, 358f, y = 122f)
        val vocals = staff(66f, 378.6f, y = 64f)
        val drums = staff(66f, 398f, y = 596.7f)
        assertEquals(1, barCountOf(piano + vocals + drums, firstMusicX = 80f))
        assertEquals(2, barCountOf(piano + staff(200f, 378.6f, y = 64f), firstMusicX = 80f))
    }

    @Test
    fun `a tune in free time plays a bar a system`() {
        assertEquals(1, barCountOf(emptyList(), firstMusicX = 29f))
    }

    @Test
    fun `a system without music plays nothing`() {
        assertEquals(0, barCountOf(emptyList(), firstMusicX = null))
        assertEquals(0, barCountOf(staff(40f), firstMusicX = null))
    }

    @Test
    fun `a system of eight bars plays four lines`() {
        assertEquals(4, playedLinesOf(8))
        assertEquals(2, playedLinesOf(3))
        assertEquals(0, playedLinesOf(0))
    }

    @Test
    fun `bar strokes are read from abc2svg's path data`() {
        assertEquals(
            listOf(NotationBarStroke(x = 10f, y = 40f), NotationBarStroke(x = 50.5f, y = 40f)),
            barStrokesOf("M10.0 40.0v-24.0M50.5 40.0v-24.0"),
        )
    }

    private fun staff(vararg xs: Float, y: Float = 40f) = xs.map { NotationBarStroke(x = it, y = y) }

    @Test
    fun `a brace, clefs, signatures, and repeat dots are not music`() {
        listOf(0xE000, 0xE050, 0xE084, 0xE262, 0xE043).forEach { assertFalse(isMusicGlyph(it), it.toString(16)) }
        listOf(0xE0A4, 0xE4E5).forEach { assertTrue(isMusicGlyph(it), it.toString(16)) }
    }

    @Test
    fun `the last slot plays every system from there on`() {
        assertEquals(2, notationSlotLines(listOf(1, 2, 3, 4), slot = 1, isLastSlot = false))
        assertEquals(9, notationSlotLines(listOf(1, 2, 3, 4), slot = 1, isLastSlot = true))
        assertEquals(0, notationSlotLines(listOf(1, 2), slot = 3, isLastSlot = false))
    }
}
