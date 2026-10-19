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

    val doubleDropD: List<String> get() = DoubleDropDChordTable.shapes

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
