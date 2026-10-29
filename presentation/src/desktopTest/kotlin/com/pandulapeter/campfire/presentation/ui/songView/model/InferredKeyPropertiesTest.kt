/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songView.model

import com.pandulapeter.campfire.chordpro.chords.ChordProChords
import com.pandulapeter.campfire.chordpro.model.ChordProBlock
import com.pandulapeter.campfire.chordpro.model.ChordProLine
import com.pandulapeter.campfire.chordpro.model.ChordProSong
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.data.model.preferences.UserPreferences.Notation
import com.pandulapeter.campfire.presentation.ui.rendering.testSongRenderer
import java.io.File
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The key a song naming none is numbered from, over :chordpro's song corpus with every `{key}` taken out and over
 * progressions generated in every key: it never fails, moves with the song, and is a key the chords belong to.
 */
class InferredKeyPropertiesTest {

    private val renderer = testSongRenderer()
    private val default = UserPreferences.ChordSpelling.Default

    private val corpus = File(requireNotNull(javaClass.getResource("/corpus")).toURI()).listFiles().orEmpty()
        .filter { it.extension == "cho" }.sortedBy { it.name }
        .associate { it.name to it.readText().lines().filterNot { line -> line.trim().startsWith("{key") }.joinToString("\n") }

    /** Diatonic progressions opening and closing on their tonic, in every key, each with the key it is in. */
    private val progressions = Random(2026).let { random ->
        (0 until 12).flatMap { tonic ->
            listOf(false, true).flatMap { isMinor ->
                (0 until 4).map {
                    val degrees = if (isMinor) listOf("", "dim", "", "m", "m", "", "") else listOf("", "m", "m", "", "", "m", "dim")
                    val steps = if (isMinor) listOf(0, 2, 3, 5, 7, 8, 10) else listOf(0, 2, 4, 5, 7, 9, 11)
                    val tonicChord = SHARPS[tonic] + if (isMinor) "m" else ""
                    val middle = List(random.nextInt(2, 7)) { random.nextInt(1, 7) }
                        .map { degree -> SHARPS[(tonic + steps[degree]) % 12] + degrees[degree] }
                    // A dominant seventh before the tonic, as a cadence in either mode has it.
                    val chords = listOf(tonicChord) + middle + (SHARPS[(tonic + 7) % 12] + "7") + tonicChord
                    Triple(tonic, isMinor, chords.joinToString(" ") { "[$it]la" })
                }
            }
        }
    }

    @Test
    fun `the key of a song naming none never fails to be worked out, whatever its chords`() {
        val odd = listOf("", "Just words\nno chords", "[N.C.]a [x]b [*Riff]c", "[C/E]a [G/B]b [D/F#]c", "[H]a [Hm]b [B]c", "[Cmaj13#11b9]a", "[1]a [2]b", "[Am]")
        (corpus.values + odd).forEach { text -> inferredKeyOf(renderer.transposedSong(text, transposition = 0, spelling = default)) }
        assertTrue(corpus.isNotEmpty())
    }

    @Test
    fun `a progression in a key is found in that key`() = progressions.forEach { (tonic, isMinor, text) ->
        assertEquals(SHARPS[tonic] + if (isMinor) "m" else "", renderer.inferredKeyOfText(text)?.let(::sharpKey), text)
    }

    @Test
    fun `the key worked out moves with the song by the interval it is transposed by`() = (corpus + progressions.associate { it.third to it.third }).forEach { (name, text) ->
        val key = renderer.inferredKeyOfText(text)?.let(::sharpKey)
        for (transposition in 1..11) {
            val moved = inferredKeyOf(renderer.transposedSong(text, transposition = transposition, spelling = default))?.let(::sharpKey)
            assertEquals(key?.let { moveKey(it, transposition) }, moved, "$name by $transposition")
        }
    }

    @Test
    fun `a song naming no key is numbered the same in every transposition, on the song details screen as in the editor's preview`() =
        (corpus + progressions.associate { it.third to it.third }).forEach { (name, text) ->
            // A song in several keys or none the walk can tell stays in letters, which move with it.
            val inferred = renderer.inferredKeyOfText(text) ?: return@forEach
            for (notation in listOf(Notation.NASHVILLE, Notation.ROMAN)) {
                val spelling = default.copy(notation = notation)
                val unmoved = chordsOf(renderer.renderSong(text, transposition = 0, spelling = spelling))
                for (transposition in 1..11) {
                    assertEquals(unmoved, chordsOf(renderer.renderSong(text, transposition = transposition, spelling = spelling)), "$name by $transposition in $notation")
                    val details = renderer.notatedSong(
                        song = renderer.transposedSong(text, transposition = transposition, spelling = spelling),
                        spelling = spelling,
                        inferredKey = renderer.movedKey(inferred, transposition, spelling),
                    )
                    assertEquals(unmoved, chordsOf(details), "$name by $transposition in $notation on the song details screen")
                }
            }
        }

    private fun chordsOf(song: ChordProSong) = song.blocks.filterIsInstance<ChordProBlock.Section>().flatMap { it.lines }
        .filterIsInstance<ChordProLine.Lyrics>().flatMap { line -> line.chords.map { it.name } }

    private fun sharpKey(key: String): String {
        val chord = requireNotNull(ChordProChords.parse(key)) { key }
        return SHARPS[chord.root] + if (3 in chord.intervals && 4 !in chord.intervals) "m" else ""
    }

    private fun moveKey(key: String, semitones: Int) = SHARPS[(SHARPS.indexOf(key.removeSuffix("m")) + semitones).mod(12)] + if (key.endsWith("m")) "m" else ""

    private companion object {
        val SHARPS = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    }
}
