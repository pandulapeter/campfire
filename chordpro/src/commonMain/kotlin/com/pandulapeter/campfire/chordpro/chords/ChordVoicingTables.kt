/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.chords

/**
 * The shapes of every tuning that has a table of its own, in the syntax of a `{define}`'s value. Within a chord they are
 * in the order they are offered in, the first being the one a chord is shown with, an order the tables are written in
 * rather than one worked out as they are read.
 *
 * A shape with no open string moves up the neck as it is, so it also stands for its chord quality on every root a
 * table has no shape of that quality for: `ChordVoicings` shifts it there, after the shapes filed under the chord
 * itself. `ChordVoicingTablesTest` checks that every shape sounds exactly the chord it is filed under, which is what
 * catches a mistyped fret, and that every fingering a shape has matches its frets; a shape that came without one has
 * none.
 */
internal object ChordVoicingTables {

    val guitar: List<String> get() = GuitarChordTable.shapes

    val dropD: List<String> get() = DropDChordTable.shapes

    /**
     * Double drop D's shapes, typed for it: its first string is a D as well, so none of [guitar]'s can be taken over as
     * it is, and a shape either rings that D, stops it, or mutes it.
     */
    val doubleDropD = listOf(
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
    )

    val openG: List<String> get() = OpenTuningChordTables.openG

    val openD: List<String> get() = OpenTuningChordTables.openD

    val openC: List<String> get() = OpenTuningChordTables.openC

    val dadgad: List<String> get() = OpenTuningChordTables.dadgad

    val ukulele: List<String> get() = UkuleleChordTable.shapes

    val mandolin: List<String> get() = MandolinChordTable.shapes

    val mandolinGdgd: List<String> get() = OpenTuningChordTables.mandolinGdgd

    val banjo: List<String> get() = BanjoChordTables.openG

    val banjoDoubleC: List<String> get() = BanjoChordTables.doubleC

    val banjoC: List<String> get() = BanjoChordTables.c

    val banjoOpenD: List<String> get() = BanjoChordTables.openD

    val banjoGModal: List<String> get() = BanjoChordTables.gModal
}
