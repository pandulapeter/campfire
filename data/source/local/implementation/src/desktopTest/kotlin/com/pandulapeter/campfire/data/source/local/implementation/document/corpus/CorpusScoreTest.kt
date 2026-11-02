/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.document.corpus

import kotlin.test.Test
import kotlin.test.assertEquals

class CorpusScoreTest {
    private val expected = """
        {title: Song}
        {artist: Someone}

        {start_of_verse: Verse 1}
        [G]Hello [C]world
        Second line
        {end_of_verse}

        {start_of_chorus}
        [D]Chorus line
        {end_of_chorus}
    """.trimIndent()

    @Test
    fun `two identical songs score 1 everywhere`() {
        assertScore(CorpusScore(1.0, 1.0, 1.0, 1.0, 1.0), CorpusScore.of(expected, expected))
    }

    @Test
    fun `case, spacing, markup, and the sharp sign do not count`() {
        val actual = expected.replace("[G]Hello [C]world", "[G]  <b>HELLO</b>   [C]world ").replace("Second", "second")
        assertScore(CorpusScore(1.0, 1.0, 1.0, 1.0, 1.0), CorpusScore.of(expected.replace("[D]", "[D♯]"), actual.replace("[D]", "[D#]")))
    }

    @Test
    fun `a chord moved by two characters lowers only the chords`() {
        // Two of the three chords found on either side: 2·2 / (3 + 3).
        val actual = expected.replace("[G]Hello [C]world", "[G]Hello wo[C]rld")
        assertScore(CorpusScore(1.0, 4.0 / 6, 1.0, 1.0, 1.0), CorpusScore.of(expected, actual))
    }

    @Test
    fun `a chord moved by one character still counts as found`() {
        val actual = expected.replace("[G]Hello [C]world", "[G]Hello w[C]orld")
        assertScore(CorpusScore(1.0, 1.0, 1.0, 1.0, 1.0), CorpusScore.of(expected, actual))
    }

    @Test
    fun `a missing section heading lowers the sections and, as a missing line, the lines`() {
        // Lines: four of five items matched, 2·4 / (5 + 4). Sections: one of two openings, 2·1 / (2 + 1), scaled by the
        // verse's two lines of the three in sections, the chorus line now lying outside any, 2·2 / (3 + 2).
        val actual = expected.replace("{start_of_chorus}\n", "").replace("{end_of_chorus}", "")
        assertScore(CorpusScore(8.0 / 9, 1.0, 2.0 / 3 * 0.8, 1.0, 1.0), CorpusScore.of(expected, actual))
    }

    @Test
    fun `a verse left inside the chorus before it lowers only the sections`() {
        // Sections: both openings right, and the three lines the expected file has in sections of the output's five, 2·3 / (3 + 5).
        val unlabelledVerse = "$expected\n\nVerse two\nVerse two again"
        val actual = unlabelledVerse.replace("{end_of_chorus}\n\n", "").plus("\n{end_of_chorus}")
        assertScore(CorpusScore(1.0, 1.0, 0.75, 1.0, 1.0), CorpusScore.of(unlabelledVerse, actual))
        assertScore(CorpusScore(1.0, 1.0, 1.0, 1.0, 1.0), CorpusScore.of(unlabelledVerse, unlabelledVerse))
    }

    @Test
    fun `a song split in two lowers only the songs`() {
        // One of the titles matches the expected one, 2·1 / (1 + 2); the untitled second song has no header to compare.
        val actual = expected.replace("{start_of_chorus}", "{new_song}\n{start_of_chorus}")
        assertScore(CorpusScore(1.0, 1.0, 1.0, 2.0 / 3, 1.0), CorpusScore.of(expected, actual))
    }

    @Test
    fun `a missing artist lowers only the header`() {
        // One of two expected fields and the one actual field match, 2·1 / (2 + 1).
        val actual = expected.replace("{artist: Someone}\n", "")
        val score = CorpusScore.of(expected, actual)
        assertScore(CorpusScore(1.0, 1.0, 1.0, 1.0, 2.0 / 3), score)
        assertEquals((4 + 2.0 / 3) / 5, score.overall, 1e-9)
    }

    @Test
    fun `a song whose title came out wrong still has its other fields compared`() {
        // Songs: no title matches. Header: the artist matches, 2·1 / (2 + 2).
        val actual = expected.replace("{title: Song}", "{title: Another}")
        assertScore(CorpusScore(1.0, 1.0, 1.0, 0.0, 0.5), CorpusScore.of(expected, actual))
    }

    private fun assertScore(expected: CorpusScore, actual: CorpusScore) {
        assertEquals(expected.lines, actual.lines, 1e-9, "lines")
        assertEquals(expected.chords, actual.chords, 1e-9, "chords")
        assertEquals(expected.sections, actual.sections, 1e-9, "sections")
        assertEquals(expected.songs, actual.songs, 1e-9, "songs")
        assertEquals(expected.header, actual.header, 1e-9, "header")
    }
}
