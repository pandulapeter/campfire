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
import kotlin.test.assertTrue

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
