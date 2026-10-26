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

    private val SongKey.name get() = root.name + if (isMinor) "m" else ""
}
