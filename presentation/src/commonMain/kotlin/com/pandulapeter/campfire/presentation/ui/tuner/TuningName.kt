/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.tuner

import com.pandulapeter.campfire.chordpro.ChordNotation
import com.pandulapeter.campfire.tuner.api.model.InstrumentTuning

/**
 * What a tuning is called, apart from the words around its note: the note is a MIDI number rather than a letter, so that
 * it is written in the reader's chord notation (Drop B is Drop H in German and Drop Si in Latin) rather than in the
 * app's language.
 */
internal sealed interface TuningName {

    /** A name with no note in it. */
    enum class Plain : TuningName { STANDARD, HALF_STEP_DOWN, FULL_STEP_DOWN, BARITONE }

    /** A name of words around one [note]: "Drop D", "Open G", "C tuning". */
    data class Noted(val kind: Kind, val note: Int) : TuningName {
        enum class Kind { DROP, DOUBLE_DROP, OPEN, HIGH, LOW, DOUBLE, KEY, MODAL }
    }

    /** A name that is the tuning's strings spelled out from the lowest, the way DADGAD is. */
    data class Spelled(val strings: List<Int>) : TuningName
}

/** The name of [tuning] on its chip, its instrument named above it, so that it needs no instrument in it. */
internal fun tuningNameOf(tuning: InstrumentTuning): TuningName = when (tuning) {
    InstrumentTuning.GUITAR,
    InstrumentTuning.GUITAR_SEVEN_STRING,
    InstrumentTuning.GUITAR_EIGHT_STRING,
    InstrumentTuning.BASS,
    InstrumentTuning.BASS_FIVE_STRING,
    InstrumentTuning.UKULELE,
    InstrumentTuning.VIOLIN,
    InstrumentTuning.MANDOLIN -> TuningName.Plain.STANDARD
    InstrumentTuning.GUITAR_HALF_STEP_DOWN,
    InstrumentTuning.GUITAR_SEVEN_STRING_HALF_STEP_DOWN,
    InstrumentTuning.GUITAR_EIGHT_STRING_HALF_STEP_DOWN,
    InstrumentTuning.BASS_HALF_STEP_DOWN,
    InstrumentTuning.BASS_FIVE_STRING_HALF_STEP_DOWN,
    InstrumentTuning.VIOLIN_HALF_STEP_DOWN,
    InstrumentTuning.MANDOLIN_HALF_STEP_DOWN -> TuningName.Plain.HALF_STEP_DOWN
    InstrumentTuning.GUITAR_FULL_STEP_DOWN,
    InstrumentTuning.GUITAR_SEVEN_STRING_FULL_STEP_DOWN,
    InstrumentTuning.GUITAR_EIGHT_STRING_FULL_STEP_DOWN,
    InstrumentTuning.BASS_FULL_STEP_DOWN,
    InstrumentTuning.BASS_FIVE_STRING_FULL_STEP_DOWN -> TuningName.Plain.FULL_STEP_DOWN
    InstrumentTuning.UKULELE_BARITONE -> TuningName.Plain.BARITONE
    // A drop tuning is named after the string it drops, the lowest.
    InstrumentTuning.GUITAR_DROP_D,
    InstrumentTuning.GUITAR_DROP_C_SHARP,
    InstrumentTuning.GUITAR_DROP_C,
    InstrumentTuning.GUITAR_DROP_B,
    InstrumentTuning.GUITAR_SEVEN_STRING_DROP_A,
    InstrumentTuning.GUITAR_EIGHT_STRING_DROP_E,
    InstrumentTuning.BASS_DROP_D,
    InstrumentTuning.BASS_DROP_C,
    InstrumentTuning.BASS_FIVE_STRING_DROP_A -> TuningName.Noted(TuningName.Noted.Kind.DROP, tuning.strings.first())
    InstrumentTuning.GUITAR_DOUBLE_DROP_D -> TuningName.Noted(TuningName.Noted.Kind.DOUBLE_DROP, tuning.strings.first())
    InstrumentTuning.GUITAR_OPEN_G, InstrumentTuning.BANJO -> TuningName.Noted(TuningName.Noted.Kind.OPEN, G)
    InstrumentTuning.GUITAR_OPEN_D, InstrumentTuning.BANJO_OPEN_D -> TuningName.Noted(TuningName.Noted.Kind.OPEN, D)
    InstrumentTuning.GUITAR_OPEN_E -> TuningName.Noted(TuningName.Noted.Kind.OPEN, E)
    InstrumentTuning.GUITAR_OPEN_A -> TuningName.Noted(TuningName.Noted.Kind.OPEN, A)
    InstrumentTuning.GUITAR_OPEN_C -> TuningName.Noted(TuningName.Noted.Kind.OPEN, C)
    InstrumentTuning.BASS_FIVE_STRING_HIGH_C -> TuningName.Noted(TuningName.Noted.Kind.HIGH, tuning.strings.last())
    InstrumentTuning.UKULELE_LOW_G -> TuningName.Noted(TuningName.Noted.Kind.LOW, tuning.strings.first())
    InstrumentTuning.UKULELE_D -> TuningName.Noted(TuningName.Noted.Kind.KEY, D)
    InstrumentTuning.BANJO_DOUBLE_C -> TuningName.Noted(TuningName.Noted.Kind.DOUBLE, C)
    InstrumentTuning.BANJO_C -> TuningName.Noted(TuningName.Noted.Kind.KEY, C)
    InstrumentTuning.BANJO_G_MODAL -> TuningName.Noted(TuningName.Noted.Kind.MODAL, G)
    InstrumentTuning.GUITAR_DADGAD,
    InstrumentTuning.VIOLIN_AEAE,
    InstrumentTuning.VIOLIN_ADAE,
    InstrumentTuning.VIOLIN_GDGD,
    InstrumentTuning.MANDOLIN_AEAE,
    InstrumentTuning.MANDOLIN_GDGD -> TuningName.Spelled(tuning.strings)
}

/**
 * [strings] spelled out in [notation], the way DADGAD is written: one-letter names run together, and a notation that
 * names a note by a syllable (`Re La Re Sol La Re`) puts a space between them, since run together they read as a word.
 */
internal fun spelledTuning(strings: List<Int>, notation: ChordNotation) = strings.joinToString(
    separator = if (notation == ChordNotation.LATIN) " " else "",
) { noteName(it, notation) }

private const val C = 0
private const val D = 2
private const val E = 4
private const val G = 7
private const val A = 9
