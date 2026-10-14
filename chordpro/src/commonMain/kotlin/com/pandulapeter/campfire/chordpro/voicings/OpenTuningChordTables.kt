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
 * The shapes of the open and modal tunings, typed for them: no standard shape carries over to strings tuned to a chord,
 * and no chord library under a license Campfire can ship has them. Open E and open A are open D and open G a tone up,
 * and the mandolin's AEAE is its GDGD a tone up, so they read these too.
 *
 * Each table holds two kinds of shape. First the chords a chart of the tuning prints in the open position, where the
 * open strings ring: the tuning's own chord, its sevenths, suspensions, and added notes with one or two fingers, and the
 * chords of its key that leave most strings open. Then one shape for every chord quality held as a barre with no open
 * string, which `ChordVoicings` moves to every root the table has no shape of that quality for, the way an open tuning is
 * played up the neck: the whole barre is the major chord, and one or two fingers above or behind it make the rest. On
 * open G, as on open A, the sixth string is muted under a barre, since it would put the chord's fifth under its root.
 *
 * In these tunings a chord's fifth may sound lowest where it is an open string ([com.pandulapeter.campfire.chordpro.model.ChordTuning.isOpen]),
 * which is how open G's G is all six strings; `ChordVoicingTablesTest` holds every other rule the standard tables keep.
 */
internal object OpenTuningChordTables {

    /** Open G, D G D G B D. */
    val openG = listOf(
        "G frets 0 0 0 0 0 0",
        "G6 frets 0 0 0 0 0 2 fingers 0 0 0 0 0 1",
        "Gmaj7 frets 0 0 0 0 0 4 fingers 0 0 0 0 0 1",
        "G7 frets 0 0 0 0 0 3 fingers 0 0 0 0 0 1",
        "G7 frets 0 0 3 0 0 0 fingers 0 0 1 0 0 0",
        "Gsus4 frets 0 0 0 0 1 0 fingers 0 0 0 0 1 0",
        "Gadd9 frets 0 0 0 2 0 0 fingers 0 0 0 1 0 0",
        "G5 frets 0 0 0 0 3 0 fingers 0 0 0 0 1 0",
        "Gm frets 0 0 0 3 3 0 fingers 0 0 0 1 1 0",
        "D frets x x 0 2 3 4 fingers 0 0 0 1 2 3",
        "D7 frets x x 0 2 1 4 fingers 0 0 0 2 1 4",
        "Em frets 2 0 2 0 0 2 fingers 1 0 2 0 0 3",
        "Em7 frets 2 0 0 0 0 0 fingers 1 0 0 0 0 0",
        "E7 frets 2 1 0 1 0 0 fingers 3 1 0 2 0 0",
        "A7 frets x 2 2 0 2 2 fingers 0 1 1 0 2 2",
        "F frets x x 3 2 1 x fingers 0 0 3 2 1 0",
        "A frets x 2 2 2 2 2 fingers 0 1 1 1 1 1",
        "Am frets x 2 2 2 1 2 fingers 0 2 2 2 1 3",
        "A7 frets x 2 2 2 2 5 fingers 0 1 1 1 1 4",
        "Bm7 frets x 4 4 2 3 4 fingers 0 3 3 1 2 4",
        "Amaj7 frets x 2 2 1 2 2 fingers 0 2 2 1 3 3",
        "A6 frets x 2 2 2 2 4 fingers 0 1 1 1 1 3",
        "Am6 frets x 2 2 2 1 4 fingers 0 2 2 2 1 4",
        "Asus4 frets x 2 2 2 3 2 fingers 0 1 1 1 2 1",
        "Csus2 frets x 5 5 5 3 5 fingers 0 2 2 2 1 3",
        "Aadd9 frets x 2 2 4 2 2 fingers 0 1 1 3 1 1",
        "A9 frets x 2 2 4 2 5 fingers 0 1 1 3 1 4",
        "A7sus4 frets x 2 2 2 3 5 fingers 0 1 1 1 2 4",
        "A5 frets x 2 2 2 x x fingers 0 1 1 1 0 0",
        "Adim frets x 2 1 2 1 1 fingers 0 2 1 3 1 1",
        "Aaug frets x 2 3 2 2 3 fingers 0 1 2 1 1 3",
        "Adim7 frets x 2 1 2 1 4 fingers 0 2 1 3 1 4",
    )

    /** Open D, D A D F♯ A D. */
    val openD = listOf(
        "D frets 0 0 0 0 0 0",
        "D7 frets 0 0 0 0 3 0 fingers 0 0 0 0 1 0",
        "Dmaj7 frets 0 0 0 0 4 0 fingers 0 0 0 0 1 0",
        "Dsus4 frets 0 0 0 1 0 0 fingers 0 0 0 1 0 0",
        "Dsus2 frets 0 0 0 3 0 2 fingers 0 0 0 2 0 1",
        "Dadd9 frets 0 0 0 0 0 2 fingers 0 0 0 0 0 1",
        "D6 frets 0 0 0 0 2 0 fingers 0 0 0 0 1 0",
        "D5 frets 0 0 0 3 0 0 fingers 0 0 0 1 0 0",
        "Bm frets x 2 0 0 2 0 fingers 0 1 0 0 2 0",
        "Bm7 frets x 2 0 0 0 0 fingers 0 1 0 0 0 0",
        "Em7 frets 2 2 2 1 2 0 fingers 2 2 2 1 3 0",
        "E7 frets 2 2 2 2 2 0 fingers 1 1 1 1 1 0",
        "E frets 2 2 2 2 2 2 fingers 1 1 1 1 1 1",
        "Em frets 2 2 2 1 2 2 fingers 2 2 2 1 3 3",
        "E7 frets 2 2 2 2 5 2 fingers 1 1 1 1 4 1",
        "F#m7 frets 4 4 2 3 4 4",
        "Emaj7 frets 2 2 1 2 2 2 fingers 2 2 1 3 3 3",
        "E6 frets 2 2 2 2 4 2 fingers 1 1 1 1 3 1",
        "Em6 frets 2 2 2 1 4 2",
        "Esus4 frets 2 2 2 3 2 2 fingers 1 1 1 2 1 1",
        "F#sus2 frets 4 4 4 2 4 4 fingers 2 2 2 1 3 3",
        "Eadd9 frets 2 2 4 2 2 2 fingers 1 1 3 1 1 1",
        "E9 frets 2 2 4 2 5 2 fingers 1 1 3 1 4 1",
        "E7sus4 frets 2 2 2 3 5 2 fingers 1 1 1 2 4 1",
        "E5 frets 2 2 2 x x x fingers 1 1 1 0 0 0",
        "Edim frets 2 1 2 1 1 2 fingers 2 1 3 1 1 4",
        "Eaug frets 2 3 2 2 3 2 fingers 1 2 1 1 3 1",
    )

    /** Open C, C G C G C E. */
    val openC = listOf(
        "C frets 0 0 0 0 0 0",
        "C7 frets 0 0 0 3 0 0 fingers 0 0 0 1 0 0",
        "Cmaj7 frets 0 0 0 4 0 0 fingers 0 0 0 1 0 0",
        "Csus4 frets 0 0 0 0 0 1 fingers 0 0 0 0 0 1",
        "Csus2 frets 0 0 0 0 2 3 fingers 0 0 0 0 1 2",
        "Cadd9 frets 0 0 0 0 2 0 fingers 0 0 0 0 1 0",
        "C6 frets 0 0 0 2 0 0 fingers 0 0 0 1 0 0",
        "C5 frets 0 0 0 0 0 3 fingers 0 0 0 0 0 1",
        "Am frets x 2 0 2 0 0 fingers 0 1 0 2 0 0",
        "Am7 frets x 2 0 0 0 0 fingers 0 1 0 0 0 0",
        "D frets 2 2 2 2 2 2 fingers 1 1 1 1 1 1",
        "Dm frets 2 2 2 2 2 1 fingers 2 2 2 2 2 1",
        "D7 frets 2 2 2 5 2 2 fingers 1 1 1 4 1 1",
        "Em7 frets 4 4 4 4 2 3 fingers 3 3 3 3 1 2",
        "Dmaj7 frets 2 2 2 2 1 2 fingers 2 2 2 2 1 3",
        "D6 frets 2 2 2 4 2 2 fingers 1 1 1 3 1 1",
        "Dm6 frets 2 2 2 4 2 1",
        "Dsus4 frets 2 2 2 2 2 3 fingers 1 1 1 1 1 2",
        "Esus2 frets 4 4 4 4 4 2 fingers 2 2 2 2 2 1",
        "Dadd9 frets 2 2 2 2 4 2 fingers 1 1 1 1 3 1",
        "D9 frets 2 2 2 5 4 2 fingers 1 1 1 4 3 1",
        "D7sus4 frets 2 2 2 5 2 3 fingers 1 1 1 4 1 2",
        "D5 frets 2 2 2 2 2 x fingers 1 1 1 1 1 0",
        "Ddim frets 2 1 2 1 2 1 fingers 2 1 3 1 4 1",
        "Daug frets 2 3 2 3 2 2 fingers 1 2 1 3 1 1",
    )

    /** DADGAD, whose open strings sound a Dsus4. */
    val dadgad = listOf(
        "Dsus4 frets 0 0 0 0 0 0",
        "D5 frets 0 0 0 2 0 0 fingers 0 0 0 1 0 0",
        "D frets 0 0 4 2 0 0 fingers 0 0 3 1 0 0",
        "D frets 0 0 0 2 0 4 fingers 0 0 0 1 0 3",
        "Dsus2 frets 0 0 2 2 0 0 fingers 0 0 1 1 0 0",
        "Dadd9 frets 0 0 2 2 0 4 fingers 0 0 1 1 0 3",
        "G frets 5 x 0 0 2 0 fingers 3 0 0 0 1 0",
        "A frets x 0 2 2 4 2 fingers 0 0 1 1 3 1",
        "Asus4 frets x 0 2 2 0 0 fingers 0 0 1 1 0 0",
        "Bm frets x 2 4 4 2 0 fingers 0 1 3 3 1 0",
        "Bm7 frets x 2 0 2 0 0 fingers 0 1 0 2 0 0",
        "Em frets 2 2 2 0 2 2 fingers 1 1 1 0 2 2",
        "Em7 frets 2 2 0 0 2 0 fingers 1 1 0 0 2 0",
        "C frets x 3 2 0 3 x fingers 0 2 1 0 3 0",
        "Cadd9 frets x 3 0 0 3 2 fingers 0 2 0 0 3 1",
        "Esus4 frets 2 2 2 2 2 2 fingers 1 1 1 1 1 1",
        "E frets 2 2 2 1 2 2 fingers 2 2 2 1 3 3",
        "F#m frets 4 4 4 2 4 4 fingers 2 2 2 1 3 3",
        "E5 frets 2 2 2 x x x fingers 1 1 1 0 0 0",
        "Esus2 frets 2 2 4 4 2 2 fingers 1 1 3 3 1 1",
        "Eadd9 frets 2 2 4 1 2 2",
        "F#7 frets 4 4 2 3 4 4 fingers 3 3 1 2 4 4",
        "F#m7 frets 4 4 2 2 4 4 fingers 2 2 1 1 3 3",
        "F#maj7 frets 4 4 3 3 4 4 fingers 2 2 1 1 3 3",
        "G6 frets 5 5 2 4 5 5",
        "F#7sus4 frets 4 4 2 4 4 4 fingers 2 2 1 3 3 3",
    )

    /** The mandolin's GDGD cross tuning, every pair of strings a G or a D, every string sounding as on the standard mandolin. */
    val mandolinGdgd = listOf(
        "G5 frets 0 0 0 0",
        "G frets 0 0 4 0 fingers 0 0 1 0",
        "G7 frets 0 0 4 3 fingers 0 0 2 1",
        "G6 frets 0 0 4 2 fingers 0 0 3 1",
        "Gmaj7 frets 0 0 4 4 fingers 0 0 3 3",
        "Gsus2 frets 0 0 2 0 fingers 0 0 1 0",
        "Gsus4 frets 0 0 5 0 fingers 0 0 1 0",
        "C frets 0 2 5 2 fingers 0 1 3 1",
        "D frets 2 4 2 0 fingers 1 3 1 0",
        "D7 frets 2 0 5 4 fingers 1 0 4 3",
        "Dsus2 frets 2 2 2 0 fingers 1 1 1 0",
        "D5 frets 2 0 2 0 fingers 1 0 2 0",
        "Em frets 0 2 4 2 fingers 0 1 3 1",
        "Em7 frets 0 0 0 2 fingers 0 0 0 1",
        "Am frets 2 2 5 2 fingers 1 1 4 1",
        "Am7 frets 2 2 5 5 fingers 1 1 3 3",
        "Bm frets 4 4 4 0 fingers 1 1 1 0",
        "Bm7 frets 2 0 4 0 fingers 1 0 3 0",
        "A5 frets 2 2 2 2 fingers 1 1 1 1",
        "C frets 5 2 5 5 fingers 3 1 4 4",
        "B7 frets 4 1 2 4 fingers 3 1 2 4",
        "Am7 frets 2 5 5 2 fingers 1 3 3 1",
        "Csus4 frets 5 3 5 5 fingers 3 1 4 4",
        "Asus2 frets 2 2 4 2 fingers 1 1 3 1",
    )
}
