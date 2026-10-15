/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.voicings

import com.pandulapeter.campfire.chordpro.model.Chord
import com.pandulapeter.campfire.chordpro.model.ChordTuning

/** Every table of shapes, under the tuning it is written for. */
internal val tablesByTuning = mapOf(
    ChordTuning.GUITAR to ChordVoicingTables.guitar,
    ChordTuning.UKULELE to ChordVoicingTables.ukulele,
    ChordTuning.MANDOLIN to ChordVoicingTables.mandolin,
    ChordTuning.GUITAR_DROP_D to ChordVoicingTables.dropD,
    ChordTuning.GUITAR_DOUBLE_DROP_D to ChordVoicingTables.doubleDropD,
    ChordTuning.GUITAR_OPEN_G to ChordVoicingTables.openG,
    ChordTuning.GUITAR_OPEN_D to ChordVoicingTables.openD,
    ChordTuning.GUITAR_OPEN_C to ChordVoicingTables.openC,
    ChordTuning.GUITAR_DADGAD to ChordVoicingTables.dadgad,
    ChordTuning.MANDOLIN_GDGD to ChordVoicingTables.mandolinGdgd,
    ChordTuning.BANJO to ChordVoicingTables.banjo,
    ChordTuning.BANJO_DOUBLE_C to ChordVoicingTables.banjoDoubleC,
    ChordTuning.BANJO_C to ChordVoicingTables.banjoC,
    ChordTuning.BANJO_OPEN_D to ChordVoicingTables.banjoOpenD,
    ChordTuning.BANJO_G_MODAL to ChordVoicingTables.banjoGModal,
)

/**
 * Whether [frets] sound [chord]'s bass, or its root, as their lowest note - or, in an open tuning, the chord's fifth
 * on an open string, which a player lets ring rather than mute.
 */
internal fun soundsBassLowest(chord: Chord, tuning: ChordTuning, frets: List<Int?>): Boolean {
    val bottom = frets.indices.filter { frets[it] != null }.minBy { tuning.strings[it] + frets[it]!! }
    val lowest = (tuning.strings[bottom] + frets[bottom]!!) % 12
    val isOpenFifth = tuning.isOpen && chord.bass == null && 7 in chord.intervals && lowest == (chord.root + 7) % 12 && frets[bottom] == 0
    return lowest == (chord.bass ?: chord.root) || isOpenFifth
}
