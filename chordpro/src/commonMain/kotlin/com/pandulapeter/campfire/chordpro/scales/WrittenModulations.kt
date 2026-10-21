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

import com.pandulapeter.campfire.chordpro.chords.ChordProChords
import com.pandulapeter.campfire.chordpro.model.Chord
import com.pandulapeter.campfire.chordpro.model.ChordProBlock
import com.pandulapeter.campfire.chordpro.model.SectionType

/**
 * A key change the chart writes out rather than directs: the modulated last chorus played in its new key, which a
 * `{transpose}` would have said in one line. [SongParts] reads the song with a `ChordProBlock.Transpose` put in front of
 * every such section, so the key in force, the part boundaries, and the weighing of recalls are the ones that directive
 * would have given.
 *
 * A section, with its continuations, or a chorus recall, is a modulated copy where an earlier one of its kind (the same
 * type, and the same label without a number where both have one) plays the same chords at one shift: the same quality on
 * every chord, every root moved by the same interval, consecutive repeats read once and a slash chord's bass left out, as
 * everywhere here. The copy may end early, being a prefix of the earlier one, but never run on: a four-chord loop is
 * the start of many a longer section in another key. It must play at least [MIN_CHORDS] chords. A shift of a fourth or
 * a fifth is never read: a section a fourth away playing the same loop (a chorus on the IV repeating the verse's
 * progression, `G D Em C` against `C G Am F`) is the song's own neighbouring key far more often than a modulation, and
 * the key decides that reading where it should.
 *
 * The shift stays in force for the sections after it that copy nothing (the outro after the last chorus) until a
 * section copies an earlier one at another shift, back to none included, and it is counted from where that earlier
 * section stood, so a half step and then another add up. A chart directing its key changes with a `{transpose}` is
 * read as the author wrote it, since its chords after one are already moved and nothing is left to find.
 */
internal object WrittenModulations {

    /**
     * The blocks of a song in [key] (null where it names none), with a `ChordProBlock.Transpose` before every section
     * that changes key by copying an earlier one, naming the key it moves to as a `{transpose}` would: [key] moved by the
     * shift, spelled as the copy spells that root.
     */
    fun of(blocks: List<ChordProBlock>, key: SongKey?): List<ChordProBlock> {
        if (blocks.any { it is ChordProBlock.Transpose }) return blocks
        val units = unitsOf(blocks)
        val result = mutableListOf<ChordProBlock>()
        var shift = 0
        var next = 0
        units.forEachIndexed { index, unit ->
            val copied = (index - 1 downTo 0).firstNotNullOfOrNull { earlier ->
                units[earlier].takeIf { it.kind matches unit.kind }?.let { shiftOf(it.chords, unit.chords) }?.let { (units[earlier].shift + it).mod(12) }
            }
            unit.shift = copied ?: shift
            while (next < unit.start) result += blocks[next++]
            if (unit.shift != shift) result += ChordProBlock.Transpose(unit.shift, key?.let { keyName(it, unit.shift, unit.roots) })
            shift = unit.shift
        }
        while (next < blocks.size) result += blocks[next++]
        return result
    }

    /** The sections [blocks] plays as they are compared: a section with its continuations, or a chorus recall. */
    private fun unitsOf(blocks: List<ChordProBlock>): List<Passage> {
        val units = mutableListOf<Passage>()
        blocks.forEachIndexed { index, block ->
            when (block) {
                is ChordProBlock.Section -> if (block.isContinuation && units.isNotEmpty()) {
                    units.last().add(SongParts.playedIn(listOf(block)))
                } else {
                    units += Passage(index, block.type to block.label?.normalized()).apply { add(SongParts.playedIn(listOf(block))) }
                }
                is ChordProBlock.ChorusRecall -> {
                    val sections = block.blocks.filterIsInstance<ChordProBlock.Section>()
                    val first = sections.firstOrNull() ?: return@forEachIndexed
                    units += Passage(index, first.type to first.label?.normalized()).apply { add(SongParts.playedIn(sections)) }
                }
                else -> Unit
            }
        }
        return units
    }

    /**
     * The interval every chord of [copy] sits from the chord of [earlier] in its place, where [copy] plays [earlier]'s
     * chords from the start, at least [MIN_CHORDS] of them, at one shift that is no fourth or fifth; else null.
     */
    private fun shiftOf(earlier: List<Chord>, copy: List<Chord>): Int? {
        if (copy.size < MIN_CHORDS || copy.size > earlier.size) return null
        val shift = (copy.first().root - earlier.first().root).mod(12)
        if (shift == FOURTH || shift == FIFTH) return null
        return shift.takeIf {
            copy.indices.all { index -> copy[index].intervals == earlier[index].intervals && (copy[index].root - earlier[index].root).mod(12) == shift }
        }
    }

    /** [key] moved by [shift], its root spelled as one of [roots] spells it, else with the key's own accidental. */
    private fun keyName(key: SongKey, shift: Int, roots: List<SpelledNote>): String {
        val pitchClass = (key.root.pitchClass + shift).mod(12)
        val root = roots.firstOrNull { it.pitchClass == pitchClass }?.name
            ?: ChordProChords.noteNames(Chord(pitchClass, listOf(0)), preferFlats = key.root.name.endsWith('b')).first()
        return root + if (key.isMinor) "m" else ""
    }

    private infix fun Pair<SectionType, String?>.matches(other: Pair<SectionType, String?>) =
        first == other.first && (second == null || other.second == null || second == other.second)

    private fun String.normalized() = trimEnd { it.isDigit() || it.isWhitespace() }.lowercase()

    /** The sections starting at the block at [start], of [kind], their chords read as compared, and their shift. */
    private class Passage(val start: Int, val kind: Pair<SectionType, String?>) {
        val chords = mutableListOf<Chord>()
        val roots = mutableListOf<SpelledNote>()
        var shift = 0

        fun add(played: List<PlayedChord>) = played.forEach { (_, root, chord) ->
            val bare = Chord(root = chord.root, intervals = chord.intervals)
            if (chords.lastOrNull() != bare) chords += bare
            roots += root
        }
    }

    private const val MIN_CHORDS = 4
    private const val FOURTH = 5
    private const val FIFTH = 7
}
