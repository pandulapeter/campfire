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
 * The mandolin's shapes in standard tuning (G D A E, one per pair of strings): the ones Campfire was first shipped with,
 * then ChordPro's.
 *
 * Within a chord the shapes are in the order they are offered in: the one every chart shows first, then the rest from
 * the most comfortable to the least (the lowest position, the fewest fingers, the smallest stretch, no muted string
 * between two that sound, the most strings sounding), an order decided once when the table was written rather than
 * worked out as it is read. Every shape here was kept only where it sounds exactly the chord it is filed under, keeps
 * the search's rules and can be held, which `ChordVoicingTablesTest` checks again. An inversion is filed under the slash
 * chord it sounds.
 *
 * Shapes from the ChordPro reference implementation's instrument configurations
 * (https://github.com/ChordPro/chordpro, `lib/ChordPro/res/config/mandolin-ly.json`), Copyright © 2010, 2018, 2023 The ChordPro
 * Team, used under the Artistic License 2.0 (https://opensource.org/license/artistic-2-0).
 */
internal object MandolinChordTable {

    val shapes = listOf(
        "C frets 0 2 3 0 fingers 0 1 2 0",
        "Cm frets 0 1 3 3 fingers 0 1 3 4",
        "Cm frets 5 5 6 8 fingers 1 1 2 4",
        "C7 frets 3 2 3 0 fingers 2 1 3 0",
        "Cm7 frets 5 5 6 6 fingers 1 1 2 2",
        "Cmaj7 frets 4 2 3 0 fingers 3 1 2 0",
        "Cmaj7 frets 5 2 2 3 fingers 4 1 1 2",
        "Csus frets 5 3 3 3 fingers 3 1 1 1",
        "Csus2 frets 5 0 3 3 fingers 3 0 1 1",
        "C6 frets 2 2 3 3 fingers 1 1 2 2",
        "Cdim7 frets 2 1 3 2 fingers 2 1 4 3",
        "Cø frets 3 1 3 2 fingers 3 1 4 2",
        "C+ frets 5 2 3 4 fingers 4 1 2 3",

        "C# frets 1 3 4 1 fingers 1 2 3 1",
        "C#m frets 1 2 4 4 fingers 1 2 3 4",
        "C#m7 frets 6 6 7 7 fingers 1 1 2 2",
        "C#maj7 frets 6 3 3 4 fingers 4 1 1 2",
        "C#sus frets 6 4 4 4 fingers 3 1 1 1",
        "C#sus2 frets 1 1 4 4 fingers 1 1 3 4",
        "C#6 frets 3 3 4 4 fingers 1 1 2 2",
        "C#ø frets 4 2 4 3 fingers 3 1 4 2",

        "D frets 2 0 0 2 fingers 1 0 0 2",
        "Dm frets 2 0 0 1 fingers 2 0 0 1",
        "D7 frets 2 0 3 2 fingers 1 0 3 2",
        "Dm7 frets 2 0 3 1 fingers 2 0 3 1",
        "Dmaj7 frets 2 0 4 2 fingers 1 0 4 2",
        "Dsus4 frets 2 0 0 3 fingers 1 0 0 3",
        "Dsus2 frets 2 0 0 0 fingers 1 0 0 0",
        "D6 frets 2 0 2 2 fingers 1 0 2 3",
        "Ddim7 frets 1 0 2 1 fingers 1 0 3 2",
        "Dø frets 1 0 3 1 fingers 1 0 3 2",
        "D+ frets 3 0 1 2 fingers 3 0 1 2",

        "Eb frets 3 1 1 3 fingers 3 1 2 4",
        "D#m frets 3 1 1 2 fingers 3 1 1 2",
        "D#7 frets 6 5 6 6 fingers 2 1 3 4",
        "D#7 frets 3 1 4 3 fingers 2 1 4 3",
        "D#m7 frets 8 8 9 9 fingers 1 2 3 4",
        "D#m7 frets 3 1 4 2 fingers 3 1 4 2",
        "D#sus frets 3 1 1 4 fingers 3 1 1 4",
        "D#sus2 frets 3 1 1 1 fingers 3 1 1 1",
        "D#6 frets 3 1 3 3 fingers 2 1 3 4",
        "D#dim7 frets 2 1 3 2 fingers 2 1 4 3",
        "D#ø frets 2 1 4 2 fingers 2 1 4 3",
        "D#+ frets 0 1 2 3 fingers 0 1 2 3",

        "E frets 1 2 2 0 fingers 1 2 3 0",
        "Em frets 0 2 2 0 fingers 0 1 2 0",
        "E7 frets 1 0 2 0 fingers 1 0 2 0",
        "Em7 frets 0 0 2 0 fingers 0 0 1 0",
        "Emaj7 frets 1 1 2 0 fingers 1 1 2 0",
        "Esus frets 4 2 0 0 fingers 3 1 0 0",
        "Esus2 frets 4 2 2 2 fingers 3 1 1 1",
        "Edim7 frets 3 2 4 3 fingers 2 1 4 3",
        "Eø frets 0 0 1 0 fingers 0 0 1 0",
        "E+ frets 1 2 3 4 fingers 1 2 3 4",

        "F frets 2 3 3 1 fingers 2 3 4 1",
        "Fm frets 1 3 3 4 fingers 1 2 3 4",
        "Fm frets 1 3 3 1 fingers 1 3 4 1",
        "Fm frets 5 3 3 4 fingers 4 1 1 2",
        "F7 frets 2 1 3 1 fingers 2 1 3 1",
        "Fm7 frets 1 1 3 1 fingers 1 1 3 1",
        "Fmaj7 frets 2 2 3 1 fingers 2 3 4 1",
        "Fsus2 frets 0 3 3 1 fingers 0 3 4 1",
        "F6 frets 2 0 3 1 fingers 2 0 3 1",
        "Fdim7 frets 1 0 2 1 fingers 1 0 3 2",
        "Fø frets 1 1 2 1 fingers 1 1 2 1",
        "F+ frets 2 3 4 5 fingers 1 2 3 4",

        "F# frets 3 4 4 2 fingers 2 3 4 1",
        "F#m frets 2 4 4 5 fingers 1 2 3 4",
        "F#m frets 2 4 4 2 fingers 1 3 4 1",
        "F#7 frets 3 2 4 2 fingers 2 1 3 1",
        "F#m7 frets 2 2 4 2 fingers 1 1 3 1",
        "F#maj7 frets 3 3 4 2 fingers 2 3 4 1",
        "F#sus2 frets 6 4 4 4 fingers 3 1 1 1",
        "F#6 frets 3 1 4 2 fingers 3 1 4 2",
        "F#dim7 frets 2 1 3 2 fingers 2 1 4 3",
        "F#ø frets 2 2 3 2 fingers 1 1 2 1",
        "F#+ frets 3 4 5 6 fingers 1 2 3 4",

        "G frets 0 0 2 3 fingers 0 0 1 2",
        "Gm frets 0 0 1 3 fingers 0 0 1 3",
        "G7 frets 0 0 2 1 fingers 0 0 2 1",
        "Gm7 frets 0 0 1 1 fingers 0 0 1 1",
        "Gmaj7 frets 0 0 2 2 fingers 0 0 1 2",
        "Gsus4 frets 0 0 3 3 fingers 0 0 2 3",
        "Gsus2 frets 0 0 0 3 fingers 0 0 0 3",
        "G6 frets 0 0 2 0 fingers 0 0 1 0",
        "Gdim7 frets 3 2 4 3 fingers 2 1 4 3",
        "Gø frets 3 3 4 3 fingers 1 1 2 1",
        "G+ frets 0 1 2 3 fingers 0 1 2 3",

        "Ab frets 1 1 3 4 fingers 1 1 3 4",
        "G#m frets 1 1 2 4 fingers 1 1 2 4",
        "G#7 frets 1 1 3 2 fingers 1 1 3 2",
        "G#m7 frets 1 1 2 2 fingers 1 1 2 2",
        "G#maj7 frets 1 1 3 3 fingers 1 1 3 3",
        "G#sus frets 1 1 4 4 fingers 1 1 3 4",
        "G#sus2 frets 1 1 1 4 fingers 1 1 1 4",
        "G#6 frets 1 1 3 1 fingers 1 1 3 1",
        "G#dim7 frets 1 0 2 1 fingers 1 0 3 2",
        "G#ø frets 1 0 2 2 fingers 1 0 2 2",
        "G#+ frets 1 2 3 4 fingers 1 2 3 4",

        "A frets 2 2 4 0 fingers 1 2 3 0",
        "A frets 2 2 4 5 fingers 1 2 3 4",
        "Am frets 2 2 3 0 fingers 1 2 3 0",
        "A7 frets 2 2 4 3 fingers 1 2 4 3",
        "Am7 frets 2 2 3 3 fingers 1 2 3 4",
        "Amaj7 frets 2 2 4 4 fingers 1 1 3 3",
        "Asus4 frets 2 2 5 0 fingers 1 2 4 0",
        "Asus frets 2 0 0 0 fingers 1 0 0 0",
        "Asus2 frets 2 2 2 0 fingers 1 2 3 0",
        "A6 frets 2 2 4 2 fingers 1 1 3 1",
        "Adim7 frets 2 1 3 2 fingers 2 1 4 3",
        "Aø frets 2 1 3 3 fingers 2 1 3 4",
        "A+ frets 2 3 4 1 fingers 2 3 4 1",

        "Bb frets 3 0 1 1 fingers 3 0 1 1",
        "A#m frets 3 3 4 6 fingers 1 1 2 4",
        "A#7 frets 3 3 5 4 fingers 1 1 3 2",
        "A#m7 frets 3 3 4 4 fingers 1 1 2 2",
        "A#maj7 frets 3 0 0 1 fingers 3 0 0 1",
        "A#sus frets 3 1 1 1 fingers 3 1 1 1",
        "A#sus2 frets 3 3 3 6 fingers 1 1 1 4",
        "A#6 frets 0 0 1 1 fingers 0 0 1 1",
        "A#9 frets 3 0 3 4 fingers 1 0 2 3",
        "A#dim7 frets 3 2 4 3 fingers 2 1 4 3",
        "A#ø frets 3 2 4 4 fingers 2 1 3 4",
        "A#+ frets 3 0 1 2 fingers 3 0 1 2",

        "B frets 4 4 6 7 fingers 1 1 3 4",
        "Bm frets 4 0 2 2 fingers 3 0 1 2",
        "B7 frets 2 1 2 2 fingers 2 1 3 4",
        "B7 frets 4 4 6 5 fingers 1 1 3 2",
        "Bm7 frets 4 4 5 5 fingers 1 2 3 4",
        "Bm7 frets 4 0 0 2 fingers 3 0 0 1",
        "Bmaj7 frets 4 1 1 2 fingers 4 1 1 2",
        "Bsus frets 4 2 2 2 fingers 3 1 1 1",
        "Bsus2 frets 4 4 4 7 fingers 1 1 1 4",
        "B6 frets 1 1 2 2 fingers 1 1 2 2",
        "Bdim7 frets 1 0 2 1 fingers 1 0 3 2",
        "Bø frets 2 0 2 1 fingers 2 0 3 1",
        "B+ frets 4 1 2 3 fingers 4 1 2 3",
    )
}
