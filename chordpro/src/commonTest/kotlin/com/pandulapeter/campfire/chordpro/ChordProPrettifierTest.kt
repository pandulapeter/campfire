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
import kotlin.time.TimeSource
import kotlin.time.Duration.Companion.seconds

class ChordProPrettifierTest {

    @Test
    fun `metadata follows the shared order and repeated directives retain their order`() {
        val raw = "{meta: link https://example.com Song}\n{tag: Second}\n{capo: 2}\n{artist: Singer}\n{t: Song}\n{tag: First}\n{meta: language en}\n{key: G}\nWords"
        assertEquals(
            "{t: Song}\n{artist: Singer}\n{key: G}\n{capo: 2}\n{tag: Second}\n{tag: First}\n{meta: language en}\n{meta: link https://example.com Song}\n\nWords\n",
            ChordProPrettifier.prettify(raw),
        )
        assertEquals(ChordProParser.parse(raw), ChordProParser.parse(ChordProPrettifier.prettify(raw)))
    }

    @Test
    fun `sections have one separating blank line and content retains its spacing`() {
        val raw = "\r\n{artist: Singer}\r\n\r\n{title: Song}\r\n{sov: Verse 1}\r\n  [G]Hello   world  \r\n{eov}\r\n{soc}\r\n[C]Sing\r\n{eoc}\r\n\r\n\r\n"
        assertEquals(
            "{title: Song}\n{artist: Singer}\n\n{sov: Verse 1}\n  [G]Hello   world  \n{eov}\n\n{soc}\n[C]Sing\n{eoc}\n",
            ChordProPrettifier.prettify(raw),
        )
        assertEquals(ChordProParser.parse(raw), ChordProParser.parse(ChordProPrettifier.prettify(raw)))
    }

    @Test
    fun `tab grid and nested environment interiors are untouched`() {
        val interior = "{sov}\n{sot}\n    G     C  \n\n\n e|--0--   \n{eot}\n{sog}\n | G . | C . |  \n\n{eog}\n{eov}"
        assertEquals("{title: Song}\n\n$interior\n", ChordProPrettifier.prettify("{title: Song}\n$interior"))
    }

    @Test
    fun `tab and grid runs stay in the paragraph they open or continue`() {
        for (raw in listOf(
            "Hello [G]world\n{start_of_tab}\ne|--0--|\n{end_of_tab}\nMore [C]words",
            "{sot}\ne|--0--|\n{eot}\nMore [C]words",
            "Hello\n{sot}\ne|-0-|\n{eot}\nMore",
        )) {
            assertEquals("$raw\n", ChordProPrettifier.prettify(raw))
            assertEquals(ChordProParser.parse(raw).blocks, ChordProParser.parse(ChordProPrettifier.prettify(raw)).blocks)
        }
    }

    @Test
    fun `balanced fragments preserve their sections and format idempotently`() {
        val random = Random(1)
        val fragments = listOf(
            "Hello [G]world", "More [C]words", "", "{chorus}",
            "{sot}\ne|--0--|\n{eot}", "{sot}\ne|--0--|\n\ne|-1-|\n{eot}",
            "{sog}\n | G . | C . |\n{eog}", "{soc}\n[C]Sing\n{eoc}", "{sov}\nVerse\n{eov}",
        )
        repeat(2000) {
            val raw = List(random.nextInt(1, 7)) { fragments[random.nextInt(fragments.size)] }.joinToString("\n")
            val formatted = ChordProPrettifier.prettify(raw)
            assertEquals(ChordProParser.parse(raw).blocks, ChordProParser.parse(formatted).blocks, raw)
            assertEquals(formatted, ChordProPrettifier.prettify(formatted), raw)
        }
    }

    @Test
    fun `delegated syntax including metadata shaped lines is literal`() {
        for (name in listOf("abc", "ly", "svg", "textblock")) {
            val interior = "{start_of_$name}\n  # literal  \n{title: Literal}\n{new_song}\n{ c d e }\n\n\n{end_of_$name}"
            assertEquals("{title: Song}\n\n$interior\n\n[G]Next\n", ChordProPrettifier.prettify("{title: Song}\n$interior\n[G]Next"))
        }
    }

    @Test
    fun `source comments settings and custom directives survive`() {
        val raw = "# Copyright\n{artist: Singer}\n{title: Song}\n# About the capo\n{capo: 2}\n{transpose: 3}\n{meta: custom Something}\n{x_custom: Value}\n{define: C frets x 3 2 0 1 0}\nWords\n{key: D}\n{unknown: Something}\nMore"
        val expected = "# Copyright\n{title: Song}\n{artist: Singer}\n# About the capo\n{capo: 2}\n{transpose: 3}\n{meta: custom Something}\n{x_custom: Value}\n{define: C frets x 3 2 0 1 0}\n\nWords\n{key: D}\n{unknown: Something}\nMore\n"
        assertEquals(expected, ChordProPrettifier.prettify(raw))
        assertEquals(ChordProParser.parse(raw), ChordProParser.parse(expected))
    }

    @Test
    fun `comments separate headings without separating them from their following lyrics`() {
        assertEquals("{c: Verse}\nWords\n\n{c: Chorus}\nSing\n\n{chorus}\n\n{np}\n\nMore\n",
            ChordProPrettifier.prettify("{c: Verse}\nWords\n{c: Chorus}\nSing\n{chorus}\n{np}\nMore"))
    }

    @Test
    fun `a collection formats each header without removing its separators`() {
        assertEquals("{title: First}\n{artist: A}\n\nWords\n\n{ns}\n\n{title: Second}\n{artist: B}\n\nMore\n",
            ChordProPrettifier.prettify("{artist: A}\n{title: First}\nWords\n{ns}\n{artist: B}\n{title: Second}\nMore"))
    }

    @Test
    fun `unfinished literal environments keep final blanks`() {
        val raw = "{sot}\n e|--0--  \n\n\n"
        assertEquals(raw, ChordProPrettifier.prettify(raw))
    }

    @Test
    fun `empty and whitespace only input stays empty`() {
        for (raw in listOf("", " ", "\r\n \r\n\t")) assertEquals("", ChordProPrettifier.prettify(raw))
    }

    @Test
    fun `formatting maps the caret to its lyric line and removed blanks to the next line`() {
        val before = "{artist: Singer}\n{title: Song}\n\n\nHello world"
        val after = ChordProPrettifier.prettify(before)
        assertEquals(after.indexOf("Hello") + 3, ChordProPrettifier.prettifiedOffset(before, after, before.indexOf("Hello") + 3))
        assertEquals(after.indexOf("Hello"), ChordProPrettifier.prettifiedOffset(before, after, before.indexOf("\n\n\n") + 2))
        for (offset in before.indices) assertEquals(offset, ChordProPrettifier.prettifiedOffset(before, before, offset))
        val indented = "  {artist: Singer}\n {title: Song}\nWords"
        val formatted = ChordProPrettifier.prettify(indented)
        assertEquals(formatted.indexOf("Singer") + 2, ChordProPrettifier.prettifiedOffset(indented, formatted, indented.indexOf("Singer") + 2))
    }

    @Test
    fun `formatting accepts arbitrary offsets and all supported line endings`() {
        for (before in listOf("", "Words", "{artist: A}\r\n{title: T}\r\nWords\r\n", "One\rTwo", "One\n\nTwo")) {
            for (after in listOf("", ChordProPrettifier.prettify(before))) {
                for (offset in -3..before.length + 3) {
                    assertTrue(ChordProPrettifier.prettifiedOffset(before, after, offset) in 0..after.length)
                }
            }
        }
        val before = "{artist: A}\r\n{title: T}\r\nWords"
        val after = ChordProPrettifier.prettify(before)
        assertEquals(after.indexOf("Words") + 2, ChordProPrettifier.prettifiedOffset(before, after, before.indexOf("Words") + 2))
        assertEquals(after.indexOf("Words"), ChordProPrettifier.prettifiedOffset(before, after, before.indexOf("\r\n", before.indexOf("{title")) + 1))
    }

    @Test
    fun `repeated lines map without searching their earlier matches again`() {
        val before = "{c: Chorus}\n".repeat(50_000) + "Last lyric"
        val after = ChordProPrettifier.prettify(before)
        val started = TimeSource.Monotonic.markNow()
        val mapped = ChordProPrettifier.prettifiedOffset(before, after, before.indexOf("Last lyric") + 4)
        assertTrue(started.elapsedNow() < 2.seconds)
        assertEquals(after.indexOf("Last lyric") + 4, mapped)
    }

    @Test
    fun `formatting is idempotent for complete and unfinished documents`() {
        val inputs = listOf(
            "{artist: A}\n{title: T}\n{sov}\n[G]Words\n{eov}\n{soc}\n[C]Sing\n{eoc}",
            "# Comment\n{t: T}\n{meta: custom Value}\nWords\n\n\nMore",
            "{start_of_svg}\n  <svg/>  \n\n",
            "{sot}\n e|--0--  \n\n\n",
            "{title: One}\n{ns}\n{title: Two}",
        )
        for (raw in inputs) {
            val formatted = ChordProPrettifier.prettify(raw)
            assertEquals(formatted, ChordProPrettifier.prettify(formatted))
            assertTrue(formatted.endsWith("\n"))
        }
    }
}
