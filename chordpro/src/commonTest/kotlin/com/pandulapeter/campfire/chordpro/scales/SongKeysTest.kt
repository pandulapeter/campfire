/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.scales

import com.pandulapeter.campfire.chordpro.ChordProParser
import com.pandulapeter.campfire.chordpro.chords.ChordProTransposer
import com.pandulapeter.campfire.chordpro.model.ChordProBlock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class SongKeysTest {

    private fun song(key: String?, capo: Int?, chords: String = "D G A Bm D G A D") = ChordProParser.parse(
        listOfNotNull(key?.let { "{key: $it}" }, capo?.let { "{capo: $it}" }).joinToString("\n") +
            "\n" + chords.split(" ").joinToString(" ") { "[$it]la" },
    )

    @Test
    fun `a capo chart keyed by the sounding key is read in the key of its shapes`() {
        assertEquals("D", SongKeys.capoChartKeyOf(song(key = "G", capo = 5)))
        assertEquals("D", SongKeys.ofChords(song(key = "G", capo = 5)))
    }

    @Test
    fun `a written key that holds the chords is their own`() {
        assertNull(SongKeys.capoChartKeyOf(song(key = "D", capo = 5)))
        assertNull(SongKeys.capoChartKeyOf(song(key = "G", capo = null)))
        assertNull(SongKeys.capoChartKeyOf(song(key = null, capo = 5)))
        assertEquals("D", SongKeys.ofChords(song(key = "D", capo = 5)))
    }

    @Test
    fun `the reading is the same in any transposition`() {
        val sounding = ChordProTransposer.transpose(song(key = "G", capo = 5), semitones = 5)
        assertEquals("G", SongKeys.capoChartKeyOf(sounding))
    }

    @Test
    fun `a capo chart's key and its key changes are renamed to the key of its chords before it moves`() {
        val parsed = song(key = "G", capo = 5, chords = "D G A D").let { it.copy(blocks = it.blocks + ChordProBlock.Transpose(2, "G")) }
        val read = SongKeys.withKeyOfChords(parsed)
        assertEquals("D", read.metadata.key)
        assertEquals("D", (read.blocks.last() as ChordProBlock.Transpose).key)
        val unchanged = song(key = "D", capo = 2)
        assertSame(unchanged, SongKeys.withKeyOfChords(unchanged))
    }
}
