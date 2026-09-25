/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChordProSummaryCacheTest {

    @Test
    fun `typing inside a plain lyric line reuses the summary after its first scan`() {
        var scans = 0
        val cache = ChordProSummaryCache { text ->
            scans++
            ChordProParser.summarize(text)
        }
        val prefix = "{title: Long Song}\n{artist: Singer}\n{key: Am}\n[Am]Chords\n"
        val suffix = "\nAnother line\n"
        val lyrics = listOf("Hello", "Hello w", "Hello wo", "Hello wor", "Hello world", "Hello wor")

        lyrics.forEach { line ->
            val text = prefix + line + suffix
            assertEquals(ChordProParser.summarize(text), cache.summaryOf(text), text)
        }
        assertEquals(2, scans)
    }

    @Test
    fun `edits to directives chords comments and line boundaries always match a full scan`() {
        val sequence = listOf(
            "{title: One}\n{key: Am}\n{transpose: 2}\nHello world",
            "{title: Two}\n{key: Am}\n{transpose: 2}\nHello world",
            "{title: Two}\n{key: G}\n{transpose: 2}\nHello world",
            "{title: Two}\n{key: G}\n{transpose: 3}\nHello world",
            "{title: Two}\n{key: G}\n{transpose: 3}\nHello [G]world",
            "{title: Two}\n{key: G}\n{transpose: 3}\nHello world",
            "{title: Two}\n{key: G}\n{transpose: 3}\nHello\nworld",
            "{title: Two}\n{key: G}\n{transpose: 3}\n# Hello\nworld",
            "{title: Two}\n{key: G}\n{transpose: 3}\n{comment: Hello}\nworld",
            "{title: Two}\n{key: G}\n{transpose: 3}\n{start_of_grid}\n| G . |\n{end_of_grid}\nworld",
            "{title: Two}\n{key: G}\n{transpose: 3}\n{start_of_tab}\nG |---|\n{end_of_tab}\nworld",
            "{title: Two}\n{key: G}\n{transpose: 3}\n{start_of_abc}\n[H] abc\n{end_of_abc}\nworld",
            "{title: One}\n{key: Am}\n{transpose: 2}\nHello world", // Revert.
            "{title: Two}\n{key: G}\n{transpose: 3}\nHello world", // Redo.
        )
        val cache = ChordProSummaryCache()
        sequence.forEach { text -> assertEquals(ChordProParser.summarize(text), cache.summaryOf(text), text) }
        cache.clear()
        assertEquals(ChordProParser.summarize(sequence.first()), cache.summaryOf(sequence.first()))
    }

    @Test
    fun `plain lyric edits inside a verse reuse the summary but tab edits are rescanned`() {
        var scans = 0
        val cache = ChordProSummaryCache { text ->
            scans++
            ChordProParser.summarize(text)
        }
        val lines = listOf(
            "{start_of_verse}\nPlain lyric\n{end_of_verse}",
            "{start_of_verse}\nPlain lyrics\n{end_of_verse}",
            "{start_of_verse}\nPlain lyrics!\n{end_of_verse}",
            "{start_of_tab}\nPlain lyrics!\n{end_of_tab}",
            "{start_of_tab}\nPlain lyrics!!\n{end_of_tab}",
        )
        lines.forEach { text -> assertEquals(ChordProParser.summarize(text), cache.summaryOf(text), text) }
        assertEquals(4, scans)
    }

    @Test
    fun `random typing anywhere in a song always matches a full scan`() {
        val random = Random(seed = 2026)
        val letters = "abcdefgHh    "
        val syntax = "[G]{}#|-/:\r"
        var scans = 0
        var edits = 0
        repeat(20) {
            val cache = ChordProSummaryCache { text ->
                scans++
                ChordProParser.summarize(text)
            }
            var text = "{title: Song}\n{key: Am}\nFirst line of the verse\nSecond line of the verse\nThird line of the verse\n" +
                "{start_of_tab}\ne|---|\n{end_of_tab}\nFirst line of the chorus\nSecond line of the chorus\n{transpose: 2}\nEnd"
            var cursor = text.length
            repeat(300) {
                // Typing the way a person does: mostly on from where the last keystroke was, now and then somewhere else.
                if (random.nextInt(30) == 0) cursor = random.nextInt(text.length + 1)
                if (cursor > 0 && random.nextInt(4) == 0) {
                    text = text.removeRange(cursor - 1, cursor)
                    cursor--
                } else {
                    text = text.substring(0, cursor) + when (random.nextInt(40)) {
                        0 -> '\n'
                        1 -> syntax.random(random)
                        else -> letters.random(random)
                    } + text.substring(cursor)
                    cursor++
                }
                edits++
                assertEquals(ChordProParser.summarize(text), cache.summaryOf(text), text)
            }
        }
        // Not a measure of anything, only a check that the test reaches the path it is about.
        assertTrue(scans < edits * 3 / 4, "$scans scans for $edits edits")
    }
}
