/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.edit

import com.pandulapeter.campfire.chordpro.language.WritingDirection.LEFT_TO_RIGHT
import com.pandulapeter.campfire.chordpro.language.WritingDirection.RIGHT_TO_LEFT
import kotlin.test.Test
import kotlin.test.assertEquals

class ChordProLineDirectionsTest {

    private val hebrewSong = "{title: שיר לדרך}\n{meta: language he}\n# הערה\n{start_of_verse}\n[Am]שלום [G]עולם\n" +
        "{c: פזמון}\n{end_of_verse}\n{start_of_tab}\ne|---3---5---|\n{end_of_tab}\n{start_of_grid}\n| Am . | G . |\n" +
        "{end_of_grid}\n{start_of_abc}\nX:1\n{end_of_abc}\n"

    private val englishSong = "{title: Road Song}\n{start_of_verse}\n[C]Hello [G]world\n[Am]שלום עולם\n{end_of_verse}\n"

    private fun directionAt(text: String, snippet: String) = ChordProLineDirections.directionOfLineAt(text, text.indexOf(snippet) + 1)

    @Test
    fun `the directives, source comments, tabs, grids, and tunes of a hebrew song are left to right`() {
        listOf("{title", "{meta", "# ", "{start_of_verse}", "{end_of_verse}", "e|---", "| Am", "X:1", "{end_of_abc}").forEach {
            assertEquals(LEFT_TO_RIGHT, directionAt(hebrewSong, it), it)
        }
    }

    @Test
    fun `a hebrew lyric starting with a chord and a hebrew comment are right to left`() {
        assertEquals(RIGHT_TO_LEFT, directionAt(hebrewSong, "[Am]שלום"))
        assertEquals(RIGHT_TO_LEFT, directionAt(hebrewSong, "{c: פזמון}"))
    }

    @Test
    fun `an english song is left to right but for its hebrew lyric line`() {
        assertEquals(LEFT_TO_RIGHT, directionAt(englishSong, "{title"))
        assertEquals(LEFT_TO_RIGHT, directionAt(englishSong, "[C]Hello"))
        assertEquals(RIGHT_TO_LEFT, directionAt(englishSong, "[Am]שלום"))
    }

    @Test
    fun `an offset past the end is on the last line`() {
        val text = "{title: Song}\nשלום"
        assertEquals(RIGHT_TO_LEFT, ChordProLineDirections.directionOfLineAt(text, text.length + 10))
    }
}
