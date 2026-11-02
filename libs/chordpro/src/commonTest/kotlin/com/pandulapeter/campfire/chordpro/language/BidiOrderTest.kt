/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.language

import kotlin.test.Test
import kotlin.test.assertEquals

class BidiOrderTest {

    private fun units(text: String) = text.map { it.toString() }

    /** [text] as it is drawn from the left in a line of [direction], brackets mirrored where they read from the right. */
    private fun drawn(text: String, direction: WritingDirection): String {
        val levels = BidiOrder.levels(units(text), direction)
        return BidiOrder.drawnOrder(levels).joinToString("") { index ->
            text[index].let { if (levels[index] % 2 == 1) BidiOrder.mirrored(it) else it }.toString()
        }
    }

    /** [drawn], a line as it is drawn from the left, in the order it is read, its brackets mirrored back. */
    private fun read(drawn: String, direction: WritingDirection): String {
        val levels = BidiOrder.levels(units(drawn), direction)
        return BidiOrder.readingOrder(levels).joinToString("") { index ->
            drawn[index].let { if (levels[index] % 2 == 1) BidiOrder.mirrored(it) else it }.toString()
        }
    }

    @Test
    fun `a Hebrew line is drawn from the right, its Latin words and numbers from the left`() {
        assertEquals("(Psalm 133:1) דחי םג", drawn("גם יחד (Psalm 133:1)", WritingDirection.RIGHT_TO_LEFT))
        assertEquals("Hine ma tov (בוט המ)", drawn("Hine ma tov (מה טוב)", WritingDirection.LEFT_TO_RIGHT))
    }

    @Test
    fun `numbers apart in a Hebrew line are drawn apart, and a number between Hebrew words in a Latin line among them`() {
        assertEquals("2 1 םולש", drawn("שלום 1 2", WritingDirection.RIGHT_TO_LEFT))
        assertEquals("abc ינש 2 דחא", drawn("abc אחד 2 שני", WritingDirection.LEFT_TO_RIGHT))
        // The one place it parts from UAX #9, so that levels read the same from either end of the line.
        assertEquals("abc םולש 3", drawn("abc שלום 3", WritingDirection.LEFT_TO_RIGHT))
    }

    @Test
    fun `a line drawn from the left reads back as it was written, in either direction`() {
        val lines = listOf(
            "הנה מה טוב ומה נעים",
            "שבת אחים גם יחד (Psalm 133)",
            "Hine ma tov (הנה מה טוב)",
            "Am   G   C",
            "   Am    G",
            "| Am | G |",
            "abc אחד 2 שני",
            "12:30 בבוקר, 2 שירים",
        )
        for (line in lines) for (direction in WritingDirection.entries) {
            assertEquals(line, read(drawn(line, direction), direction), "$line, $direction")
        }
    }

    @Test
    fun `a letter of the supplementary planes' right-to-left blocks reads from the right`() {
        val units = listOf("a", "\uD802\uDD00", "\uD802\uDD01")
        assertEquals(listOf(0, 2, 1), BidiOrder.drawnOrder(BidiOrder.levels(units, WritingDirection.LEFT_TO_RIGHT)).toList())
    }
}
