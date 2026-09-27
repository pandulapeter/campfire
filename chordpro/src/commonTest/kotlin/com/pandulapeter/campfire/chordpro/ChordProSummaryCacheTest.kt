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
    fun `typing lyrics on a line with chords reuses the summary after its first scan`() {
        var scans = 0
        val cache = ChordProSummaryCache { text ->
            scans++
            ChordProParser.summarize(text)
        }
        val prefix = "{title: Long Song}\n{artist: Singer}\n{key: Am}\n[Am]Chords\n"
        val suffix = "\nAnother line\n"
        val lyrics = listOf("Hello", "Hello w", "Hello wo", "Hello wor", "Hello world", "Hello wor")

        lyrics.forEach { line ->
            val text = prefix + "[Am]" + line + " [G]x" + suffix
            assertEquals(ChordProParser.summarize(text), cache.summaryOf(text), text)
        }
        assertEquals(2, scans)
    }

    @Test
    fun `typing inside a bracket always matches a full scan`() {
        val cache = ChordProSummaryCache()
        listOf("[A]x", "[Am]x", "[Am7]x", "[Hm7]x").forEach { line ->
            val text = "{key: B}\n$line"
            assertEquals(ChordProParser.summarize(text), cache.summaryOf(text), text)
        }
        assertEquals("Bb", cache.summaryOf("{key: B}\n[Hm7]x").metadata.key)
    }

    @Test
    fun `deleting the start of a line that makes it a comment or a directive matches a full scan`() {
        val cache = ChordProSummaryCache()
        listOf("{key: Am}\na# note [G]x", "{key: Am}\n# note [G]x", "{key: Am}\na{title: X}", "{key: Am}\n{title: X}").forEach { text ->
            assertEquals(ChordProParser.summarize(text), cache.summaryOf(text), text)
        }
    }

    @Test
    fun `typing after an unclosed bracket matches a full scan`() {
        val cache = ChordProSummaryCache()
        listOf("x [Am y", "x [Am yz", "x [Am yz]").forEach { text ->
            assertEquals(ChordProParser.summarize(text), cache.summaryOf(text), text)
        }
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

    @Test
    fun `random typing in a song with chords on every line always matches a full scan`() {
        val random = Random(seed = 2027)
        val letters = "abcdefgHh    "
        val syntax = "[G]{}#|-/:\r"
        var scans = 0
        var edits = 0
        repeat(20) {
            val cache = ChordProSummaryCache { text ->
                scans++
                ChordProParser.summarize(text)
            }
            var text = "{title: Song}\n{key: Am}\n[Am]First line of the [C]verse\n[G]Second line of the [Am]verse\n" +
                "[F]Third line of the [E]verse\n[C]First line of the [G]chorus\n[Am]Second line of the [F]chorus\nEnd"
            var cursor = text.length
            repeat(300) {
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
        // Every body line holds a chord, so this is what the cache reusing summaries on chorded lines is checked by.
        assertTrue(scans < edits / 2, "$scans scans for $edits edits")
    }
}
