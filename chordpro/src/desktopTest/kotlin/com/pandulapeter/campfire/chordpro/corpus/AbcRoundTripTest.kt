/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.corpus

import com.pandulapeter.campfire.chordpro.ChordProParser
import com.pandulapeter.campfire.chordpro.abc.AbcToChordPro
import com.pandulapeter.campfire.chordpro.chords.ChordProTransposer
import com.pandulapeter.campfire.chordpro.model.ChordProBlock
import com.pandulapeter.campfire.chordpro.model.ChordProLine
import com.pandulapeter.campfire.chordpro.model.ChordProSong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Every ABC tune of the corpus, and the `abc` blocks of its ChordPro songs, through the conversion and the transposition. */
class AbcRoundTripTest {

    @Test
    fun `a converted tune keeps every line of it in its block, and is titled by it`() = Corpus.files("abc").forEach { (name, tune) ->
        val song = AbcToChordPro.convert(tune, fallbackTitle = name)
        val parsed = ChordProParser.parse(song.text)
        assertEquals(tune.trim().lines(), tune(parsed), name)
        assertEquals(tune.lines().first { it.startsWith("T:") }.removePrefix("T:"), parsed.metadata.title, name)
        assertTrue(song.hasChords, name)
    }

    @Test
    fun `a tune transposed up and back down is the tune it was`() =
        (Corpus.files("abc").mapValues { AbcToChordPro.convert(it.value).text } + Corpus.chordPro.filterValues { "{start_of_abc" in it }).forEach { (name, text) ->
            for (semitones in listOf(1, 2, 5, 11)) {
                val moved = ChordProTransposer.transposeText(text, semitones)
                assertTrue(tune(ChordProParser.parse(moved)).any { it.startsWith("%%transpose") }, "$name by $semitones")
                assertEquals(tune(ChordProParser.parse(text)), tune(ChordProParser.parse(ChordProTransposer.transposeText(moved, -semitones))), "$name by $semitones")
            }
        }

    private fun tune(song: ChordProSong) = song.blocks.filterIsInstance<ChordProBlock.Section>().flatMap { it.lines }
        .filterIsInstance<ChordProLine.Verbatim>().filter { it.environment == "abc" }.map { it.text }
}
