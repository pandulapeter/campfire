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
 * Double drop D's shapes, typed for it: its first string is a D as well, so none of [GuitarChordTable]'s can be taken
 * over as it is, and a shape either rings that D, stops it, or mutes it. The scale chords' shapes follow
 * ([ChordVoicingTables]).
 */
internal object DoubleDropDChordTable {

    val shapes = listOf(
        "D frets 0 0 0 2 3 4 fingers 0 0 0 1 2 4",
        "Dm frets 0 0 0 2 3 3 fingers 0 0 0 1 2 3",
        "D7 frets 0 0 0 2 1 4 fingers 0 0 0 2 1 4",
        "Dmaj7 frets 0 0 0 2 2 4 fingers 0 0 0 1 2 4",
        "Dsus2 frets 0 0 0 2 3 2 fingers 0 0 0 1 3 2",
        "Dsus4 frets 0 0 0 2 3 5 fingers 0 0 0 1 2 4",
        "D5 frets 0 0 0 2 3 0 fingers 0 0 0 1 2 0",
        "D5 frets 0 0 0 x x x fingers 0 0 0 0 0 0",
        "Eb5 frets 1 1 1 x x x fingers 1 1 1 0 0 0",
        "E frets 2 2 2 1 0 x fingers 2 3 4 1 0 0",
        "Em frets 2 2 2 0 0 2 fingers 1 2 3 0 0 4",
        "E7 frets 2 2 0 1 0 0 fingers 2 3 0 1 0 0",
        "Em7 frets 2 2 0 0 0 0 fingers 1 2 0 0 0 0",
        "F frets 3 3 3 2 1 x fingers 3 3 3 2 1 0",
        "Fm frets 3 3 3 1 1 x fingers 3 3 3 1 1 0",
        "F7 frets 3 3 1 2 1 x fingers 3 3 1 2 1 0",
        "Fm7 frets 3 3 1 1 1 x fingers 3 3 1 1 1 0",
        "Fmaj7 frets x x 3 2 1 2 fingers 0 0 4 2 1 3",
        "G frets 5 2 0 0 0 5 fingers 3 1 0 0 0 4",
        "G7 frets 5 2 0 0 0 3 fingers 4 1 0 0 0 2",
        "Gmaj7 frets 5 2 0 0 0 4 fingers 4 1 0 0 0 3",
        "G/B frets x 2 0 0 0 5 fingers 0 1 0 0 0 4",
        "A frets x 0 2 2 2 x fingers 0 0 1 2 3 0",
        "Am frets x 0 2 2 1 x fingers 0 0 2 3 1 0",
        "A7 frets x 0 2 0 2 x fingers 0 0 2 0 3 0",
        "Am7 frets x 0 2 0 1 x fingers 0 0 2 0 1 0",
        "Asus2 frets x 0 2 2 0 x fingers 0 0 1 2 0 0",
        "Asus4 frets x 0 2 2 3 x fingers 0 0 1 2 3 0",
        "A5 frets x 0 2 2 x x fingers 0 0 1 2 0 0",
        "Bb frets x 1 3 3 3 x fingers 0 1 2 3 4 0",
        "Bbm frets x 1 3 3 2 x fingers 0 1 3 4 2 0",
        "Bb7 frets x 1 3 1 3 x fingers 0 1 3 1 4 0",
        "Bbm7 frets x 1 3 1 2 x fingers 0 1 3 1 2 0",
        "B7 frets x 2 1 2 0 x fingers 0 2 1 3 0 0",
        "Bm7 frets x 2 0 2 0 0 fingers 0 1 0 2 0 0",
        "C frets x 3 2 0 1 x fingers 0 3 2 0 1 0",
        "C7 frets x 3 2 3 1 x fingers 0 3 2 4 1 0",
        "Cmaj7 frets x 3 2 0 0 x fingers 0 3 2 0 0 0",
        "Cadd9 frets x 3 2 0 3 0 fingers 0 2 1 0 3 0",

        "F#dim frets 4 3 4 2 x x fingers 3 2 4 1 0 0",
        "Adim frets x 0 1 2 1 1 fingers 0 0 1 2 1 1",
        "Bbdim frets x 1 2 3 2 2 fingers 0 1 2 4 3 3",
        "Caug frets x 3 2 1 1 2 fingers 0 4 2 1 1 3",
        "Eaug frets x x 2 1 1 2 fingers 0 0 2 1 1 3",
        "Ebsus2 frets 1 1 3 3 4 1 fingers 1 1 2 3 4 1",
        "Gsus2 frets 5 0 0 0 3 0 fingers 3 0 0 0 1 0",
        "Bbsus2 frets x 1 3 3 1 3 fingers 0 1 2 3 1 4",
        "Ebsus4 frets 1 1 1 1 4 1 fingers 1 1 1 1 4 1",
        "Esus4 frets 2 2 2 2 0 2 fingers 1 1 1 1 0 2",
        "Fsus4 frets 3 3 3 3 1 3 fingers 3 3 3 3 1 4",
        "Ebmaj7 frets 1 1 0 0 3 0 fingers 1 1 0 0 3 0",
        "Emaj7 frets 2 2 1 1 0 1 fingers 3 4 1 1 0 2",
        "Ebm7b5 frets 1 4 4 2 2 1 fingers 1 4 4 2 2 1",
        "Am7b5 frets x 0 1 0 1 1 fingers 0 0 2 0 1 1",
        "Bbm7b5 frets x 1 2 1 2 2 fingers 0 1 2 1 3 4",
        "Ebdim7 frets 1 3 1 2 1 4 fingers 1 3 1 2 1 4",
        "Adim7 frets x 0 4 5 4 4 fingers 0 0 1 2 1 1",
        "Bbdim7 frets x 1 2 0 2 2 fingers 0 1 2 0 3 4",
        "Bdim7 frets x 2 3 1 3 3 fingers 0 2 3 1 4 4",
        "Dmmaj7 frets 0 0 0 2 2 3 fingers 0 0 0 1 1 2",
        "Emmaj7 frets 2 2 2 0 0 1 fingers 2 3 4 0 0 1",
        "Fmmaj7 frets 3 3 3 1 1 2 fingers 3 3 3 1 1 2",
        "Bbmmaj7 frets x 1 3 2 2 3 fingers 0 1 3 2 2 4",
        "C#maj7#5 frets 11 12 10 10 10 10 fingers 2 3 1 1 1 1",
        "Dmaj7#5 frets 0 4 4 3 3 0 fingers 0 2 3 1 1 0",
        "Ebmaj7#5 frets 1 2 0 0 0 0 fingers 1 2 0 0 0 0",
        "Emaj7#5 frets 2 3 1 1 1 1 fingers 2 3 1 1 1 1",
        "D7#5 frets 0 3 4 3 3 0 fingers 0 1 2 1 1 0",
        "Eb7#5 frets 1 2 1 0 2 1 fingers 1 3 1 0 4 2",
        "E7#5 frets 2 3 0 1 1 0 fingers 2 3 0 1 1 0",
        "F#7#5 frets x x 4 3 3 2 fingers 0 0 4 2 3 1",
        "F7b5 frets 3 2 3 2 4 x fingers 2 1 3 1 4 0",
        "Bb7b5 frets x 1 2 1 3 2 fingers 0 1 2 1 4 3",
        "Emaj7b5 frets 2 1 1 1 4 1 fingers 2 1 1 1 4 1",
        "Gmaj7b5 frets 5 4 4 0 0 4 fingers 3 1 1 0 0 2",
        "Amaj7b5 frets x 0 1 1 2 1 fingers 0 0 1 1 2 1",
        "Bbmaj7b5 frets x 1 2 2 3 2 fingers 0 1 2 2 4 3",
    )
}
