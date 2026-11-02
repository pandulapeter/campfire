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

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChordProLiteralLinesTest {

    private val song = "{title: Song}\n[C]Hello\n{sog}\n| C . | G . |\n{eog}\n{start_of_tab}\ne|--0--|\n{end_of_tab}\n" +
        "{start_of_abc}\n[CEG]\n{end_of_abc}\n{start_of_chorus}\n[G]Sing\n{end_of_chorus}\n"

    @Test
    fun `the lines of a grid, a tab, and delegated notation are literal`() {
        assertTrue(ChordProLiteralLines.isLiteralAt(song, song.indexOf("| C")))
        assertTrue(ChordProLiteralLines.isLiteralAt(song, song.indexOf("G . |")))
        assertTrue(ChordProLiteralLines.isLiteralAt(song, song.indexOf("e|")))
        assertTrue(ChordProLiteralLines.isLiteralAt(song, song.indexOf("[CEG]")))
    }

    @Test
    fun `lyrics, sections, and the directives opening an environment are not literal`() {
        assertFalse(ChordProLiteralLines.isLiteralAt(song, song.indexOf("[C]Hello")))
        assertFalse(ChordProLiteralLines.isLiteralAt(song, song.indexOf("{sog}") + 2))
        assertFalse(ChordProLiteralLines.isLiteralAt(song, song.indexOf("[G]Sing")))
        assertFalse(ChordProLiteralLines.isLiteralAt(song, song.length))
    }
}
