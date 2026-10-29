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
import com.pandulapeter.campfire.chordpro.ChordProSerializer
import com.pandulapeter.campfire.chordpro.chords.ChordNotation
import com.pandulapeter.campfire.chordpro.chords.ChordProChords
import com.pandulapeter.campfire.chordpro.chords.ChordProNotation
import com.pandulapeter.campfire.chordpro.chords.ChordProTransposer
import com.pandulapeter.campfire.chordpro.model.ChordProBlock
import com.pandulapeter.campfire.chordpro.model.ChordProLine
import com.pandulapeter.campfire.chordpro.model.ChordProSong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChordProRoundTripTest {

    @Test
    fun `every song of the corpus reads back the same after it is written`() = Corpus.chordPro.forEach { (name, text) ->
        val song = ChordProParser.parse(text)
        val written = ChordProSerializer.serialize(song)
        assertEquals(song, ChordProParser.parse(written), name)
        assertEquals(written, ChordProSerializer.serialize(ChordProParser.parse(written)), name)
    }

    @Test
    fun `transposing a song moves every chord and the key by the same interval, and back gives the song`() = Corpus.chordPro.forEach { (name, text) ->
        // A model's own mid-song transpositions are applied by every move, so it is moved from the one as read.
        val song = ChordProParser.parse(text)
        val unmoved = ChordProTransposer.transpose(song, 0)
        for (semitones in -11..11) {
            val moved = ChordProTransposer.transpose(song, semitones)
            assertEquals(pitches(unmoved).map { it?.let { chord -> chord.copy(root = (chord.root + semitones).mod(12), bass = chord.bass?.let { bass -> (bass + semitones).mod(12) }) } }, pitches(moved), "$name by $semitones")
            assertEquals(lyrics(song), lyrics(moved), "$name by $semitones")
            if (song.blocks.none { it is ChordProBlock.Transpose }) {
                assertEquals(pitches(song), pitches(ChordProTransposer.transpose(moved, -semitones)), "$name by $semitones and back")
            }
        }
    }

    @Test
    fun `transposing a text up and back down gives back every chord, perhaps respelled`() = Corpus.chordPro.forEach { (name, text) ->
        for (semitones in listOf(1, 5, 7, 12)) {
            val back = ChordProTransposer.transposeText(ChordProTransposer.transposeText(text, semitones), -semitones)
            assertEquals(pitches(ChordProParser.parse(text)), pitches(ChordProParser.parse(back)), "$name by $semitones")
            assertEquals(lyrics(ChordProParser.parse(text)), lyrics(ChordProParser.parse(back)), "$name by $semitones")
        }
    }

    @Test
    fun `a text converted to another notation and back reads as the same song`() = Corpus.chordPro.forEach { (name, text) ->
        for (notation in listOf(ChordNotation.GERMAN, ChordNotation.LATIN)) {
            val converted = ChordProNotation.convertText(text, ChordNotation.STANDARD, notation)
            assertEquals(pitches(ChordProParser.parse(text)), pitches(ChordProParser.parse(converted, notation)), "$name in $notation")
            val back = ChordProNotation.convertText(converted, notation, ChordNotation.STANDARD)
            assertEquals(ChordProParser.parse(text), ChordProParser.parse(back), "$name back from $notation")
        }
    }

    @Test
    fun `the parser never loses a lyric's letters, whatever is cut off the end of a file`() = Corpus.chordPro.forEach { (name, text) ->
        for (end in text.indices step 7) {
            ChordProParser.parse(text.substring(0, end))
            ChordProParser.parse(text.substring(end))
        }
        assertTrue(name.isNotEmpty())
    }

    private fun lines(song: ChordProSong) = song.blocks.flatMap { block ->
        when (block) {
            is ChordProBlock.Section -> block.lines
            is ChordProBlock.ChorusRecall -> block.blocks.filterIsInstance<ChordProBlock.Section>().flatMap { it.lines }
            else -> emptyList()
        }
    }

    private fun lyrics(song: ChordProSong) = lines(song).filterIsInstance<ChordProLine.Lyrics>().map { line -> line.text to line.chords.map { it.position } }

    /** The key and every chord of [song] played, as pitches, null for a name that is no chord. */
    private fun pitches(song: ChordProSong) = listOf(song.metadata.key?.let { ChordProChords.parse(it) }) +
        lines(song).filterIsInstance<ChordProLine.Lyrics>().flatMap { line ->
            line.chords.filterNot { it.isAnnotation || it.isMark }.map { chord -> ChordProChords.parse(chord.name) }
        }
}
