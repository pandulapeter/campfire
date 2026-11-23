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

    @Test
    fun `a lyric cut into pieces between spoken names a line each is put back together under its chords`() {
        val sheet = listOf(
            "Verse 1", "Em", "G", "(E minor)", "Today is ", "(G major)", "gonna be the day",
            "D", "A", "Rea", "(D major)", "lized what to do ", "(A major)",
            "Em", "G", "(E minor)", "(G major)",
            "C", "(C major)", "1995",
        ).joinToString("\n")
        val result = ChordSheetConverter.convert(ChordSheet.ofPlainText(sheet), guessesTitle = false).single()

        assertTrue("[Em]Today is [G]gonna be the day\nRea[D]lized what to do [A]\n[Em] [G]\n[C]" in result, result)
    }

    @Test
    fun `pieces of a lyric end at a heading after the row's last chord`() {
        assertEquals(
            listOf("Em    G", "Words", "Chorus"),
            ChordSheetSpelledChords.lines(listOf("Em", "G", "(E minor)", "Words ", "(G major)", "Chorus")).map { it.trimEnd() },
        )
    }

    @Test
    fun `a lyric line with no spoken name after its chord row is not joined to the next`() {
        val lines = listOf("G", "First line", "Second line (G major)")

        assertEquals(lines, ChordSheetSpelledChords.lines(lines))
    }
}
