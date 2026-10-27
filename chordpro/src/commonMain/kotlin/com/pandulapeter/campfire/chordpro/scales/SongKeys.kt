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
import com.pandulapeter.campfire.chordpro.model.ChordProSong

/**
 * The one reading of a song's `{key}`, which everything naming the key a song is in (the song details screen's header and
 * Transposition control, the song lists, the degrees, the scales) goes through. A capo chart may write its `{key}` as
 * the key its shapes are in (`{key: D}{capo: 2}` over D shapes, sounding E) or as the key that sounds
 * (`{key: G}{capo: 5}` over D shapes), and only its chords tell the two apart: the written key is the chords' own unless
 * it holds nothing of the song whole and the key a capo's shapes play does, the rule [SongScaleGroups] reads every part
 * by. Every reading is shift-invariant, so a song moved by any transposition, the file's capo counted in or not, reads
 * alike.
 */
public object SongKeys {

    /**
     * The key the chords of [song], in the standard notation, are written in, as a key name (`D`, `Am`): its `{key}` (or
     * the key a `{transpose}` before its first chord names), read as [capoChartKeyOf] reads a capo chart's. Null where the
     * song names no key or plays no chord. What a caller counts the chords from where `SongScaleSuggestions` finds no home.
     */
    public fun ofChords(song: ChordProSong): String? = SongScaleGroups.keyOfChordsIn(song)?.first?.name

    /**
     * The key the chords of [song], a capo chart in the standard notation, are in where its `{key}` names the sounding key
     * over them (`D` for `{key: G}{capo: 5}` over D shapes), and null where the written key is the chords' own: one that
     * names no `{capo}` or no `{key}`, or whose key holds its chords. Read where the file is scanned, since the lists
     * name a song's key without its text.
     */
    public fun capoChartKeyOf(song: ChordProSong): String? {
        if (song.metadata.key.isNullOrBlank() || (song.metadata.capo ?: 0) == 0) return null
        return SongScaleGroups.keyOfChordsIn(song)?.takeIf { it.second }?.first?.name
    }

    /**
     * [song], parsed and not transposed yet, with its `{key}` read as the key its chords are in ([capoChartKeyOf]), and
     * every `{transpose}` naming it so, so that whatever moves the song moves the key the page names along with its
     * chords. Unchanged where the written key is the chords' own already.
     */
    public fun withKeyOfChords(song: ChordProSong): ChordProSong {
        val key = capoChartKeyOf(song) ?: return song
        return song.copy(
            metadata = song.metadata.copy(key = key),
            blocks = song.blocks.map { block -> if (block is ChordProBlock.Transpose && block.key != null) block.copy(key = key) else block },
        )
    }

    /**
     * The key each chord of [song], in the standard notation, is counted from where it is first played after a key change,
     * by the chord's name as written: [homeKey] (a key name, `F`, `Am`) moved as far as the key in force there sits from
     * the one at [home], a block of [song] (its first chord when null), its root spelled as a chord played in that key
     * spells it, else with [homeKey]'s accidental. A chord first played in the key at [home] is left out. A key change is
     * one the song directs with a `{transpose}` or one it writes out ([WrittenModulations]), as the Scales section reads
     * them, so a verse played again a minor third up counts its chords from its own key. A chord played on both sides of
     * a key change is counted where it is first played, as a numbering names it. Empty where [homeKey] names no note.
     */
    public fun keysOfChords(song: ChordProSong, home: ChordProBlock?, homeKey: String): Map<String, String> {
        val tonic = ChordProChords.pitchClass(rootNameOf(homeKey) ?: return emptyMap()) ?: return emptyMap()
        val blocks = WrittenModulations.of(song.blocks, key = null)
        val firstOffsets = LinkedHashMap<String, Int>()
        ChordProChords.forEachName(song.copy(blocks = blocks)) { name, offset -> firstOffsets.getOrPut(name) { offset } }
        val homeOffset = home?.let { block -> offsetAt(blocks, blocks.indexOfFirst { it === block }) } ?: firstOffsets.values.firstOrNull() ?: 0
        val shifts = firstOffsets.mapValues { (_, offset) -> (offset - homeOffset).mod(12) }.filterValues { it != 0 }
        val suffix = if (homeKey.trim().endsWith('m')) "m" else ""
        val keyNames = shifts.values.distinct().associateWith { shift ->
            val root = (tonic + shift).mod(12)
            val spelled = shifts.filterValues { it == shift }.keys.firstNotNullOfOrNull { name -> rootNameOf(name)?.takeIf { ChordProChords.pitchClass(it) == root } }
            (spelled ?: ChordProChords.noteNames(Chord(root, listOf(0)), preferFlats = 'b' in rootNameOf(homeKey).orEmpty()).first()) + suffix
        }
        return shifts.mapValues { (_, shift) -> keyNames.getValue(shift) }
    }

    /** The semitones the last `{transpose}` before the block at [index] of [blocks] moves by, 0 before any. */
    private fun offsetAt(blocks: List<ChordProBlock>, index: Int) =
        blocks.take(index.coerceAtLeast(0)).filterIsInstance<ChordProBlock.Transpose>().lastOrNull()?.semitones ?: 0

    /** The note a chord or key name starts with, as written (`Ab` for `Abm7`), null where it starts with none. */
    private fun rootNameOf(name: String): String? {
        val trimmed = name.trim()
        val letter = trimmed.firstOrNull()?.takeIf { it in 'A'..'G' } ?: return null
        return letter + trimmed.getOrNull(1)?.takeIf { it == '#' || it == 'b' }?.toString().orEmpty()
    }

    private val SongKey.name get() = root.name + if (isMinor) "m" else ""
}
