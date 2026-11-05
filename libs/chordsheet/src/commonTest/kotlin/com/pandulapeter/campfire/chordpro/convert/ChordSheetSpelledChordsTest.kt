/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.convert

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChordSheetSpelledChordsTest {

    @Test
    fun `chords listed a row each are set where the lyric spells their names out`() {
        val sheet = """
            Chorus 4
            E
            (E major)I'll be there for you
            C#m
            (C sharp minor)These five words I swear to you
            A
            When you (A major)breathe, I wanna be the air for you
            B
            D
            A
            E
            (B major)I'll be (D major)there (A major)for (E major)you
            I'd live and I'd die for you
        """.trimIndent()
        val result = ChordSheetConverter.convert(ChordSheet.ofPlainText(sheet), guessesTitle = false).single()

        assertTrue("[E]I'll be there for you\n[C#m]These five words I swear to you\nWhen you [A]breathe, I wanna be the air for you" in result, result)
        assertTrue("[B]I'll be [D]there [A]for [E]you\nI'd live and I'd die for you" in result, result)
    }

    @Test
    fun `chords the lyric names no place for follow it on a row of their own`() {
        assertEquals(listOf("A", "Words", "B D"), ChordSheetSpelledChords.lines(listOf("A", "B D", "(A major)Words")))
    }

    @Test
    fun `a lyric in parentheses that names no chord of the rows above stays as it is`() {
        val lines = listOf("G", "(A little bit) more", "D", "(A)nd so on")

        assertEquals(lines, ChordSheetSpelledChords.lines(lines))
    }
}
