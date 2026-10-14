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

/**
 * The five-string banjo's shapes, one table per tuning, typed for them: none of the chord libraries Campfire takes shapes
 * from has the banjo, and no tuning of it is another one with every string moved by one interval.
 *
 * The first fret of every shape is the short fifth string's, which is only ever open or muted
 * ([com.pandulapeter.campfire.chordpro.model.ChordInstrument.droneString]): open where its note is one of the chord's,
 * muted where it is not, so that every shape sounds exactly the chord it is filed under. Each table holds the chords a
 * chart of the tuning prints in the open position first, in the order a chart offers them, then one shape for a few
 * chord qualities held with no open string on the four long strings, which `ChordVoicings` moves to every root the
 * table has no shape of that quality for, its fifth string open or muted for the chord it lands on.
 */
internal object BanjoChordTables {

    /** Open G, g D G B D: the banjo's standard tuning. */
    val openG = listOf(
        "G frets 0 0 0 0 0",
        "G7 frets 0 0 0 0 3 fingers 0 0 0 0 1",
        "G7 frets 0 3 0 0 0 fingers 0 1 0 0 0",
        "Gmaj7 frets 0 0 0 0 4 fingers 0 0 0 0 1",
        "G6 frets 0 0 0 0 2 fingers 0 0 0 0 1",
        "Gsus4 frets 0 0 0 1 0 fingers 0 0 0 1 0",
        "Gm frets 0 0 3 3 0 fingers 0 0 1 2 0",
        "C frets 0 2 0 1 2 fingers 0 2 0 1 3",
        "C7 frets 0 2 3 1 2 fingers 0 2 4 1 3",
        "D frets x 0 2 3 4 fingers 0 0 1 2 3",
        "D7 frets x 0 2 1 4 fingers 0 0 2 1 4",
        "D7 frets x 4 2 1 0 fingers 0 3 2 1 0",
        "Dm frets x 0 2 3 3 fingers 0 0 1 2 3",
        "Dm7 frets x 0 2 1 3 fingers 0 0 2 1 3",
        "Em frets 0 2 0 0 2 fingers 0 1 0 0 2",
        "Em7 frets 0 2 0 0 0 fingers 0 1 0 0 0",
        "E frets x 2 1 0 2 fingers 0 2 1 0 3",
        "E7 frets x 0 1 0 2 fingers 0 0 1 0 2",
        "Am frets x 2 2 1 2 fingers 0 2 3 1 4",
        "Am7 frets 0 2 2 1 2 fingers 0 2 3 1 4",
        "A frets x 2 2 2 2 fingers 0 1 1 1 1",
        "A7 frets 0 2 2 2 2 fingers 0 1 1 1 1",
        "F frets x 3 2 1 3 fingers 0 3 2 1 4",
        "F7 frets x 1 2 1 3 fingers 0 1 2 1 3",
        "Fm frets x 3 1 1 3 fingers 0 3 1 1 4",
        "Fm7 frets x 1 1 1 3 fingers 0 1 1 1 3",
        "Fmaj7 frets x 3 2 1 2 fingers 0 4 2 1 3",
        "F6 frets x 3 2 3 3 fingers 0 2 1 3 4",
        "B7 frets x 1 2 0 1 fingers 0 1 3 0 2",
        "Bm frets x 0 4 3 4 fingers 0 0 2 1 3",
        "Bm7 frets x 0 2 0 0 fingers 0 0 1 0 0",
        "Bb frets x 3 3 3 3 fingers 0 1 1 1 1",
        "Bdim frets x 3 4 3 3 fingers 0 1 2 1 1",
        "Eb frets 0 1 3 4 1 fingers 0 1 2 3 1",
        "F#m frets x 4 2 2 4 fingers 0 3 1 1 4",
        "Asus4 frets x 2 2 3 2 fingers 0 1 1 2 1",
        "Caug frets x 2 1 1 2 fingers 0 2 1 1 3",
        "Cdim7 frets x 1 2 1 4 fingers 0 1 2 1 4",
    )

    /** Double C, g C G C D. */
    val doubleC = listOf(
        "C frets 0 0 0 0 2 fingers 0 0 0 0 1",
        "C7 frets 0 0 3 0 2 fingers 0 0 2 0 1",
        "Cmaj7 frets 0 0 4 0 2 fingers 0 0 2 0 1",
        "C6 frets 0 0 2 0 2 fingers 0 0 1 0 2",
        "Cm frets 0 0 0 0 1 fingers 0 0 0 0 1",
        "Csus4 frets 0 0 0 0 3 fingers 0 0 0 0 1",
        "Csus2 frets 0 0 0 0 0",
        "F frets x 0 2 0 3 fingers 0 0 1 0 2",
        "F7 frets x 0 2 3 3 fingers 0 0 1 2 3",
        "Fm frets x 0 1 0 3 fingers 0 0 1 0 3",
        "G frets 0 2 4 2 0 fingers 0 1 3 2 0",
        "G7 frets 0 2 4 2 3 fingers 0 1 3 2 4",
        "Am frets x 0 2 0 2 fingers 0 0 1 0 2",
        "Am7 frets 0 0 2 0 2 fingers 0 0 1 0 2",
        "A frets x 1 2 1 2 fingers 0 1 2 1 3",
        "A7 frets 0 1 2 1 2 fingers 0 1 2 1 3",
        "D frets x 2 2 2 4 fingers 0 1 1 1 3",
        "D7 frets x 0 2 2 4 fingers 0 0 1 2 4",
        "Dm frets x 2 2 2 3 fingers 0 1 1 1 2",
        "Dm7 frets x 0 2 2 3 fingers 0 0 1 2 3",
        "Em frets 0 4 4 4 2 fingers 0 2 3 4 1",
        "Em7 frets 0 2 0 2 2 fingers 0 1 0 2 3",
        "E7 frets x 2 1 2 2 fingers 0 2 1 3 4",
        "Bb frets x 2 3 2 3 fingers 0 1 2 1 3",
        "Bm frets x 2 4 2 4 fingers 0 1 3 1 4",
    )

    /** C tuning, g C G B D: open G with its fourth string a tone down. */
    val c = listOf(
        "C frets 0 0 0 1 2 fingers 0 0 0 1 2",
        "C7 frets 0 0 3 1 2 fingers 0 0 3 1 2",
        "Cmaj7 frets 0 0 0 0 2 fingers 0 0 0 0 1",
        "C6 frets 0 0 2 1 2 fingers 0 0 2 1 3",
        "Cm frets 0 0 0 1 1 fingers 0 0 0 1 2",
        "F frets x 0 2 1 3 fingers 0 0 2 1 3",
        "F7 frets x 3 2 1 3 fingers 0 3 2 1 4",
        "Fm frets x 0 1 1 3 fingers 0 0 1 2 4",
        "G frets 0 2 0 0 0 fingers 0 1 0 0 0",
        "G7 frets 0 2 0 0 3 fingers 0 1 0 0 3",
        "Gmaj7 frets 0 2 0 0 4 fingers 0 1 0 0 4",
        "G6 frets 0 2 0 0 2 fingers 0 1 0 0 2",
        "Gsus4 frets 0 0 0 1 0 fingers 0 0 0 1 0",
        "Gm frets 0 2 3 3 0 fingers 0 1 2 3 0",
        "D frets x 2 2 3 4 fingers 0 1 1 2 3",
        "D7 frets x 2 2 1 4 fingers 0 2 3 1 4",
        "Dm frets x 2 2 3 3 fingers 0 1 1 2 3",
        "Dm7 frets x 2 2 1 3 fingers 0 2 3 1 4",
        "Em frets 0 4 0 0 2 fingers 0 3 0 0 1",
        "Em7 frets 0 2 0 0 2 fingers 0 1 0 0 2",
        "E frets x 4 1 0 2 fingers 0 4 1 0 2",
        "E7 frets x 2 1 0 2 fingers 0 2 1 0 3",
        "Am frets x 0 2 1 2 fingers 0 0 2 1 3",
        "Am7 frets 0 0 2 1 2 fingers 0 0 2 1 3",
        "A frets x 1 2 2 2 fingers 0 1 2 3 4",
        "A7 frets 0 1 2 2 2 fingers 0 1 2 3 4",
        "Bb frets x 2 3 3 3 fingers 0 1 2 3 4",
        "Bm frets x 2 4 3 4 fingers 0 1 3 2 4",
        "B7 frets x 3 2 0 1 fingers 0 3 2 0 1",
    )

    /** Open D, f♯ D F♯ A D. */
    val openD = listOf(
        "D frets 0 0 0 0 0",
        "D7 frets 0 0 0 3 0 fingers 0 0 0 1 0",
        "Dmaj7 frets 0 0 0 4 0 fingers 0 0 0 1 0",
        "D6 frets 0 0 0 2 0 fingers 0 0 0 1 0",
        "Dsus4 frets x 0 1 0 0 fingers 0 0 1 0 0",
        "G frets x 0 1 2 0 fingers 0 0 1 2 0",
        "G7 frets x 0 1 2 3 fingers 0 0 1 2 3",
        "Gm frets x 0 1 1 0 fingers 0 0 1 2 0",
        "A frets x 2 3 4 2 fingers 0 1 2 3 1",
        "A7 frets x 2 3 4 5 fingers 0 1 2 3 4",
        "Asus4 frets x 0 3 0 2 fingers 0 0 2 0 1",
        "Bm frets 0 0 0 2 0 fingers 0 0 0 1 0",
        "Bm7 frets 0 0 3 2 0 fingers 0 0 2 1 0",
        "B7 frets 0 1 3 2 1 fingers 0 1 3 2 1",
        "Em frets x 2 1 2 2 fingers 0 2 1 3 4",
        "Em7 frets x 0 1 2 2 fingers 0 0 1 2 3",
        "E7 frets x 0 2 2 2 fingers 0 0 1 2 3",
        "E frets x 2 2 2 2 fingers 0 1 1 1 1",
        "F#m frets 0 4 3 4 4 fingers 0 2 1 3 4",
        "C frets x 2 1 3 2 fingers 0 2 1 4 3",
        "F frets x 3 3 3 3 fingers 0 1 1 1 1",
        "Fm frets x 3 2 3 3 fingers 0 2 1 3 4",
    )

    /** G modal, or sawmill tuning, g D G C D. */
    val gModal = listOf(
        "Gsus4 frets 0 0 0 0 0",
        "G frets 0 0 4 2 0 fingers 0 0 2 1 0",
        "G7 frets 0 0 4 2 3 fingers 0 0 3 1 2",
        "Gm frets 0 0 3 2 0 fingers 0 0 2 1 0",
        "C frets 0 2 0 0 2 fingers 0 1 0 0 2",
        "C7 frets 0 2 3 0 2 fingers 0 1 3 0 2",
        "D frets x 0 2 2 4 fingers 0 0 1 2 4",
        "D7 frets x 0 2 0 4 fingers 0 0 1 0 3",
        "Dm frets x 0 2 2 3 fingers 0 0 1 2 3",
        "Dm7 frets x 0 2 0 3 fingers 0 0 1 0 3",
        "Dsus4 frets 0 0 2 2 0 fingers 0 0 1 2 0",
        "Am frets x 2 2 0 2 fingers 0 1 2 0 3",
        "Am7 frets 0 2 2 0 2 fingers 0 1 2 0 3",
        "A frets x 2 2 1 2 fingers 0 2 3 1 4",
        "A7 frets 0 2 2 1 2 fingers 0 2 3 1 4",
        "Em frets 0 2 4 4 2 fingers 0 1 3 4 2",
        "Em7 frets 0 0 0 2 2 fingers 0 0 0 1 2",
        "E7 frets x 0 1 2 2 fingers 0 0 1 2 3",
        "F frets x 3 2 0 3 fingers 0 3 2 0 4",
        "Bb frets x 0 3 2 3 fingers 0 0 2 1 3",
        "Bm frets x 0 4 2 4 fingers 0 0 3 1 4",
        "F#m frets x 4 2 1 4 fingers 0 3 2 1 4",
    )
}
