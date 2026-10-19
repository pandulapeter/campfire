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
 *
 * Every table but the guitar's, the ukulele's, and drop D's, which have them already, ends in the shapes of the chords a
 * seven-note scale stacks (`ScaleChords`) in the keys its typed shapes leave without one, since a scale sheet draws only
 * the tables' shapes: each picked from the search's shapes of its chord, the most comfortable among those reaching the
 * most keys filed or moved, an open-position one where that is the least work. Where one of them, moved to a key, would
 * come before the shape that key was shown with, that shape is filed under its chord there too, so that every chord a
 * table answered before keeps its shape. A few span five frets, where their chord has no shape within four.
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
