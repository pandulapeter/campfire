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

import com.pandulapeter.campfire.chordpro.chords.ChordNotation
import com.pandulapeter.campfire.chordpro.chords.ChordProChords
import com.pandulapeter.campfire.chordpro.chords.ChordProNotation
import com.pandulapeter.campfire.chordpro.chords.ChordProTransposer
import com.pandulapeter.campfire.chordpro.edit.ChordProPrettifier
import com.pandulapeter.campfire.chordpro.model.ChordProBlock
import com.pandulapeter.campfire.chordpro.model.ChordProLine
import com.pandulapeter.campfire.chordpro.model.ChordProSong
import com.pandulapeter.campfire.chordpro.syntax.ChordProTokens
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * The module's promises held against generated songs: headers, sections and their recalls, chords of every shape and
 * words in brackets that are none, markup, comments, tabs, grids, `abc` tunes, and mid-song changes, each line picked
 * from a fixed vocabulary by a seeded random. A failure names its seed and the smallest text, cut line by line, that
 * still breaks the rule.
 *
 * Every rule but the round trip is also held to stray, unclosed, and repeated directives, which a text half typed is
 * full of. The round trip is held to well-formed songs only, as the corpus's are: [ChordProSerializer] writes the model
 * as a file would say it, and some of what the parser recovers from a broken file (a comment in a tab that never had a
 * line) no file says.
 */
internal class GeneratedSongPropertyTest {

    @Test
    fun `a generated song reads back the same after it is written`() = forEachGenerated(isWellFormed = true) { text ->
        val song = ChordProParser.parse(text)
        val written = ChordProSerializer.serialize(song)
        assertEquals(song, ChordProParser.parse(written), "Written as:\n$written")
    }

    @Test
    fun `prettifying a generated song keeps its blocks and changes nothing the second time`() = forEachGenerated { text ->
        val formatted = ChordProPrettifier.prettify(text)
        assertEquals(ChordProParser.parse(text).blocks, ChordProParser.parse(formatted).blocks, "Formatted as:\n$formatted")
        assertEquals(formatted, ChordProPrettifier.prettify(formatted))
    }

    @Test
    fun `transposing a generated text up and back down gives back every chord, perhaps respelled`() = forEachGenerated { text ->
        for (semitones in listOf(1, 2, 5, 6, 7, 11)) {
            val up = ChordProTransposer.transposeText(text, semitones)
            val back = ChordProTransposer.transposeText(up, -semitones)
            assertEquals(pitches(ChordProParser.parse(text)), pitches(ChordProParser.parse(back)), "By $semitones, back as:\n$back")
            assertEquals(lyrics(ChordProParser.parse(text)), lyrics(ChordProParser.parse(back)), "By $semitones, back as:\n$back")
        }
    }

    @Test
    fun `transposing a generated text by an octave keeps every pitch`() = forEachGenerated { text ->
        for (semitones in listOf(12, -12)) {
            val moved = ChordProTransposer.transposeText(text, semitones)
            assertEquals(pitches(ChordProParser.parse(text)), pitches(ChordProParser.parse(moved)), "By $semitones, as:\n$moved")
        }
    }

    @Test
    fun `transposing a generated song moves every chord by the interval, and back gives its pitches`() = forEachGenerated { text ->
        val song = ChordProParser.parse(text)
        // A model's own mid-song transpositions are applied by every move, so the moves are compared with the one as read.
        val unmoved = ChordProTransposer.transpose(song, 0)
        for (semitones in listOf(-7, -1, 1, 3, 6, 12)) {
            val moved = ChordProTransposer.transpose(song, semitones)
            val expected = pitches(unmoved).map { chord ->
                chord?.let { it.copy(root = (it.root + semitones).mod(12), bass = it.bass?.let { bass -> (bass + semitones).mod(12) }) }
            }
            assertEquals(expected, pitches(moved), "By $semitones")
            if (song.blocks.none { it is ChordProBlock.Transpose }) {
                assertEquals(pitches(song), pitches(ChordProTransposer.transpose(moved, -semitones)), "By $semitones and back")
            }
        }
    }

    @Test
    fun `a generated text converted to German or Latin and back reads as the same song`() = forEachGenerated { text ->
        for (notation in listOf(ChordNotation.GERMAN, ChordNotation.LATIN)) {
            val converted = ChordProNotation.convertText(text, ChordNotation.STANDARD, notation)
            assertEquals(pitches(ChordProParser.parse(text)), pitches(ChordProParser.parse(converted, notation)), "In $notation:\n$converted")
            val back = ChordProNotation.convertText(converted, notation, ChordNotation.STANDARD)
            assertEquals(
                ChordProParser.parse(text).withChordRowSpacingFolded(),
                ChordProParser.parse(back).withChordRowSpacingFolded(),
                "Back from $notation:\n$back",
            )
        }
    }

    /** Runs [check] on every generated song, reporting the seed and the shortest failing text cutting lines reaches. */
    private fun forEachGenerated(isWellFormed: Boolean = false, check: (String) -> Unit) = SEEDS.forEach { seed ->
        val random = Random(seed)
        repeat(ITERATIONS) { iteration ->
            val lines = random.nextSong(isWellFormed)
            if (!lines.passes(check)) {
                val shrunk = lines.shrunk(check)
                val message = runCatching { check(shrunk.joinToString("\n")) }.exceptionOrNull()?.message
                fail("Seed $seed, iteration $iteration, shrunk to:\n${shrunk.joinToString("\n")}\n---\n$message")
            }
        }
    }

    private fun List<String>.passes(check: (String) -> Unit) = runCatching { check(joinToString("\n")) }.exceptionOrNull() !is AssertionError

    private fun List<String>.shrunk(check: (String) -> Unit): List<String> {
        var current = this
        var isShrinking = true
        while (isShrinking) {
            isShrinking = false
            var index = 0
            while (index < current.size) {
                val candidate = current.filterIndexed { candidateIndex, _ -> candidateIndex != index }
                if (!candidate.passes(check)) {
                    current = candidate
                    isShrinking = true
                } else {
                    index++
                }
            }
        }
        return current
    }

    private fun Random.nextSong(isWellFormed: Boolean): List<String> = buildList {
        repeat(nextInt(0, 6)) { add(nextHeaderLine()) }
        repeat(nextInt(1, 7)) { addAll(nextPart(isWellFormed)) }
    }

    private fun Random.nextHeaderLine() = when (nextInt(12)) {
        0 -> "{title: ${nextWords()}}"
        1 -> "{artist: ${nextWords()}}"
        2 -> "{key: ${nextKey()}}"
        3 -> "{tempo: ${nextInt(40, 220)}}"
        4 -> "{time: ${listOf("3/4", "4/4", "6/8", "2/2")[nextInt(4)]}}"
        5 -> "{capo: ${nextInt(0, 8)}}"
        6 -> "{tag: ${nextWords()}}"
        7 -> "# ${nextWords()}"
        8 -> "{transpose: ${nextInt(-5, 6)}}"
        9 -> "{define: ${nextChord()} base-fret 1 frets x 3 2 0 1 0}"
        10 -> "{subtitle: ${nextWords()}}"
        else -> "{meta: album ${nextWords()}}"
    }

    private fun Random.nextPart(isWellFormed: Boolean): List<String> = when (nextInt(16)) {
        0, 1 -> nextSection("verse")
        2 -> nextSection("chorus")
        3 -> nextSection("bridge")
        4 -> listOf("{chorus}")
        5 -> listOf("{${listOf("c", "ci", "comment", "comment_italic", "highlight")[nextInt(5)]}: ${nextLyrics()}}")
        6 -> listOf("{start_of_tab}", "e|--${nextInt(0, 12)}--3--|", "B|--1-----|", "{end_of_tab}")
        7 -> listOf("{start_of_grid}", "| ${nextChord()} . | ${nextChord()} . |", "{end_of_grid}")
        // Closed by their own end: another environment's end closes one too, which is how the parser recovers a block
        // whose end was forgotten, and what it leaves is no song a writer of the model would write.
        8 -> when (nextInt(3)) {
            0 -> listOf("{start_of_abc}", "X:1", "K:${nextKey()}", "\"${nextChord()}\"cde|f2", "{end_of_abc}")
            1 -> listOf("{start_of_textblock}", nextWords(), "{end_of_textblock}")
            else -> listOf("{start_of_ly}", "{ c d e }", "{end_of_ly}")
        }
        9 -> listOf(listOf("{key: ${nextKey()}}", "{tempo: ${nextInt(40, 220)}}", "{time: 3/4}", "{transpose: ${nextInt(-3, 4)}}", "{np}")[nextInt(5)])
        10 -> listOf("")
        11 -> if (isWellFormed) listOf("") else listOf(ODD_LINES[nextInt(ODD_LINES.size)])
        else -> List(nextInt(1, 4)) { nextLyrics() }
    }

    private fun Random.nextSection(name: String): List<String> {
        val label = if (nextBoolean()) ": ${nextWords()}" else ""
        return listOf("{start_of_$name$label}") + List(nextInt(0, 4)) { if (nextInt(5) == 0) "" else nextLyrics() } + "{end_of_$name}"
    }

    private fun Random.nextLyrics(): String = buildString {
        repeat(nextInt(0, 6)) {
            when (nextInt(9)) {
                0, 1, 2 -> append("[${nextChord()}]")
                3 -> append("[${NOT_CHORDS[nextInt(NOT_CHORDS.size)]}]")
                4 -> {
                    val tag = "biu"[nextInt(3)]
                    append("<$tag>${nextWords()}</$tag>")
                }
                else -> append(nextWords())
            }
            if (nextBoolean()) append(' ')
        }
    }

    private fun Random.nextWords() = List(nextInt(1, 4)) { WORDS[nextInt(WORDS.size)] }.joinToString(" ")

    private fun Random.nextKey() = NOTES[nextInt(NOTES.size)] + if (nextBoolean()) "m" else ""

    private fun Random.nextChord(): String {
        val chord = NOTES[nextInt(NOTES.size)] + QUALITIES[nextInt(QUALITIES.size)] + if (nextInt(5) == 0) "/" + NOTES[nextInt(NOTES.size)] else ""
        return if (nextInt(10) == 0) "($chord)" else chord
    }

    /**
     * [this] with the spaces of every tab line but a staff line folded: a row of chord names in a tab keeps each name in
     * its column, which a longer name can only do by taking a space next to it, and the way back cannot tell whether it
     * took one (`Eb G` and `Eb  G` are both `Mib Sol`), so a cramped row's spacing does not come back.
     */
    private fun ChordProSong.withChordRowSpacingFolded(): ChordProSong = copy(blocks = blocks.map { it.withChordRowSpacingFolded() })

    private fun ChordProBlock.withChordRowSpacingFolded(): ChordProBlock = when (this) {
        is ChordProBlock.Section -> copy(
            lines = lines.map { line ->
                if (line !is ChordProLine.Tab || ChordProTokens.isStaffLine(line.text)) line
                else line.copy(text = line.text.split(' ').filter { it.isNotEmpty() }.joinToString(" "))
            },
        )
        is ChordProBlock.ChorusRecall -> copy(blocks = blocks.map { it.withChordRowSpacingFolded() })
        else -> this
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

    private companion object {
        val SEEDS = 1..4
        const val ITERATIONS = 100
        val NOTES = listOf("C", "C#", "Db", "D", "D#", "Eb", "E", "F", "F#", "Gb", "G", "G#", "Ab", "A", "A#", "Bb", "B")
        val QUALITIES = listOf("", "", "m", "7", "maj7", "m7", "sus4", "sus2", "dim", "aug", "add9", "6", "9", "m7b5", "7#9", "13")
        val NOT_CHORDS = listOf("Intro", "Chorus 2x", "*x2", "N.C.", "Break", "Ebony", "|", "Am Bm")
        val ODD_LINES = listOf(
            "{start_of_verse}", "{end_of_chorus}", "{chorus: Again}", "{comment_box: [G]boxed}", "{column_break}",
            "{chordcolour: #ff0000}", "{chordcolour}", "{textcolour: blue}", "{end_of_textblock}", "{end_of_ly}", "{soc}", "{eoc}", "{sot: Riff [G]}", "{eot}", "[am]la [e7]lo [Bbm]li",
            "[*x2] [|] [G] . . . | [C]", "{key:}", "{tempo:}", "{x_custom: value}", "{new_page}",
        )
        val WORDS = listOf("la", "love", "Bad", "Eb", "and", "the", "fire", "árvíz", "שלום", "кино", "x2", "A", "!", "dog's", "<intro>", "a{b}", "x]y", "\u00A0", "am", "|", "%")
    }
}
