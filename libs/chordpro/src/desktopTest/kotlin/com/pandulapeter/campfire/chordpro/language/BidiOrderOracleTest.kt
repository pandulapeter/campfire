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

import java.text.Bidi
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [BidiOrder] against the JDK's implementation of the Unicode bidirectional algorithm, on generated lines of the words a
 * song mixes: Hebrew with and without points, Arabic with and without vowels, Latin words, and punctuation ending a word.
 * Numbers are left out, where [BidiOrder] departs from the algorithm on purpose, so that a line's levels come out the
 * same in reading and in drawn order (`Psalm 133` one run in a right-to-left line, a number among Latin words theirs).
 */
class BidiOrderOracleTest {

    private val hebrew = listOf("שלום", "שִׁיר", "בְּשׁוּב", "ילדה")
    private val arabic = listOf("ليل", "سَلَامٌ", "قلبي")
    private val latin = listOf("Hello", "world", "rock", "été")
    private val endings = listOf("", "", "", ",", ".", "!", "?")

    @Test
    fun `a line's drawn order is the one the Unicode bidirectional algorithm gives`() {
        val random = Random(9)
        repeat(2000) { attempt ->
            val direction = if (attempt % 2 == 0) WritingDirection.RIGHT_TO_LEFT else WritingDirection.LEFT_TO_RIGHT
            val pools = listOf(hebrew, arabic, latin)
            val line = List(random.nextInt(1, 7)) { pools.random(random).random(random) + endings.random(random) }.joinToString(" ")
            val units = units(line)
            val levels = BidiOrder.levels(units.map { line.substring(it.first, it.last + 1) }, direction)
            val bidi = Bidi(line, if (direction == WritingDirection.RIGHT_TO_LEFT) Bidi.DIRECTION_RIGHT_TO_LEFT else Bidi.DIRECTION_LEFT_TO_RIGHT)
            val expected = units.map { bidi.getLevelAt(it.first).toByte() }.toByteArray()
            val drawn = Array<Any>(units.size) { it }
            Bidi.reorderVisually(expected, 0, drawn, 0, units.size)
            assertEquals(drawn.map { it as Int }, BidiOrder.drawnOrder(levels).toList(), "$direction $line")
        }
    }

    /** The characters of [line], a letter with its marks as one. */
    private fun units(line: String): List<IntRange> = buildList {
        line.forEachIndexed { index, character ->
            if (character.category == CharCategory.NON_SPACING_MARK && isNotEmpty()) set(lastIndex, last().first..index) else add(index..index)
        }
    }
}
