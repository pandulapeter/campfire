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

import kotlin.test.Test
import kotlin.test.assertEquals

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
}
