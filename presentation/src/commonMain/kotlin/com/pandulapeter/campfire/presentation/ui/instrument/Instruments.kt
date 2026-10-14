/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.instrument

import com.pandulapeter.campfire.chordpro.model.ChordInstrument
import com.pandulapeter.campfire.chordpro.model.ChordTuning
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.tuner.api.model.InstrumentTuning
import com.pandulapeter.campfire.tuner.api.model.StringCourses

/**
 * The tunings the tuner knows for the instrument, in the order the sheet offers them, the standard one first. Empty for
 * [UserPreferences.Instrument.KEYBOARD] and [UserPreferences.Instrument.OTHER], which the tuner reads chromatically. The
 * twelve-string guitar's are the guitar's own, see [isTwelveString].
 */
internal val UserPreferences.Instrument.tunings: List<InstrumentTuning>
    get() = when (this) {
        UserPreferences.Instrument.GUITAR,
        UserPreferences.Instrument.GUITAR_TWELVE_STRING -> listOf(
            InstrumentTuning.GUITAR,
            InstrumentTuning.GUITAR_DROP_D,
            InstrumentTuning.GUITAR_HALF_STEP_DOWN,
            InstrumentTuning.GUITAR_FULL_STEP_DOWN,
            InstrumentTuning.GUITAR_DROP_C_SHARP,
            InstrumentTuning.GUITAR_DROP_C,
            InstrumentTuning.GUITAR_DROP_B,
            InstrumentTuning.GUITAR_DOUBLE_DROP_D,
            InstrumentTuning.GUITAR_OPEN_G,
            InstrumentTuning.GUITAR_OPEN_D,
            InstrumentTuning.GUITAR_OPEN_E,
            InstrumentTuning.GUITAR_OPEN_A,
            InstrumentTuning.GUITAR_OPEN_C,
            InstrumentTuning.GUITAR_DADGAD,
        )
        UserPreferences.Instrument.GUITAR_SEVEN_STRING -> listOf(
            InstrumentTuning.GUITAR_SEVEN_STRING,
            InstrumentTuning.GUITAR_SEVEN_STRING_DROP_A,
            InstrumentTuning.GUITAR_SEVEN_STRING_HALF_STEP_DOWN,
            InstrumentTuning.GUITAR_SEVEN_STRING_FULL_STEP_DOWN,
        )
        UserPreferences.Instrument.GUITAR_EIGHT_STRING -> listOf(
            InstrumentTuning.GUITAR_EIGHT_STRING,
            InstrumentTuning.GUITAR_EIGHT_STRING_DROP_E,
            InstrumentTuning.GUITAR_EIGHT_STRING_HALF_STEP_DOWN,
            InstrumentTuning.GUITAR_EIGHT_STRING_FULL_STEP_DOWN,
        )
        UserPreferences.Instrument.BASS -> listOf(
            InstrumentTuning.BASS,
            InstrumentTuning.BASS_DROP_D,
            InstrumentTuning.BASS_HALF_STEP_DOWN,
            InstrumentTuning.BASS_FULL_STEP_DOWN,
            InstrumentTuning.BASS_DROP_C,
        )
        UserPreferences.Instrument.BASS_FIVE_STRING -> listOf(
            InstrumentTuning.BASS_FIVE_STRING,
            InstrumentTuning.BASS_FIVE_STRING_DROP_A,
            InstrumentTuning.BASS_FIVE_STRING_HIGH_C,
            InstrumentTuning.BASS_FIVE_STRING_HALF_STEP_DOWN,
            InstrumentTuning.BASS_FIVE_STRING_FULL_STEP_DOWN,
        )
        UserPreferences.Instrument.UKULELE -> listOf(
            InstrumentTuning.UKULELE,
            InstrumentTuning.UKULELE_LOW_G,
            InstrumentTuning.UKULELE_D,
            InstrumentTuning.UKULELE_BARITONE,
        )
        UserPreferences.Instrument.BANJO -> listOf(
            InstrumentTuning.BANJO,
            InstrumentTuning.BANJO_DOUBLE_C,
            InstrumentTuning.BANJO_C,
            InstrumentTuning.BANJO_OPEN_D,
            InstrumentTuning.BANJO_G_MODAL,
        )
        UserPreferences.Instrument.MANDOLIN -> listOf(
            InstrumentTuning.MANDOLIN,
            InstrumentTuning.MANDOLIN_HALF_STEP_DOWN,
            InstrumentTuning.MANDOLIN_AEAE,
            InstrumentTuning.MANDOLIN_GDGD,
        )
        UserPreferences.Instrument.VIOLIN -> listOf(
            InstrumentTuning.VIOLIN,
            InstrumentTuning.VIOLIN_HALF_STEP_DOWN,
            InstrumentTuning.VIOLIN_AEAE,
            InstrumentTuning.VIOLIN_ADAE,
            InstrumentTuning.VIOLIN_GDGD,
        )
        UserPreferences.Instrument.KEYBOARD,
        UserPreferences.Instrument.OTHER -> emptyList()
    }

/**
 * `:chordpro`'s instrument for this one, null for the instruments it has no shapes for (the longer guitars, the basses,
 * the violin, and Other): a diagram for the six strings of a standard guitar would be wrong on any of them, which is
 * worse than none.
 */
internal val UserPreferences.Instrument.chordInstrument: ChordInstrument?
    get() = when (this) {
        UserPreferences.Instrument.GUITAR,
        UserPreferences.Instrument.GUITAR_TWELVE_STRING -> ChordInstrument.GUITAR
        UserPreferences.Instrument.UKULELE -> ChordInstrument.UKULELE
        UserPreferences.Instrument.MANDOLIN -> ChordInstrument.MANDOLIN
        UserPreferences.Instrument.BANJO -> ChordInstrument.BANJO
        UserPreferences.Instrument.KEYBOARD -> ChordInstrument.KEYBOARD
        UserPreferences.Instrument.GUITAR_SEVEN_STRING,
        UserPreferences.Instrument.GUITAR_EIGHT_STRING,
        UserPreferences.Instrument.BASS,
        UserPreferences.Instrument.BASS_FIVE_STRING,
        UserPreferences.Instrument.VIOLIN,
        UserPreferences.Instrument.OTHER -> null
    }

/** Whether the chord diagrams can be drawn for the instrument. */
internal val UserPreferences.Instrument.hasDiagrams get() = chordInstrument != null

/**
 * Whether the instrument is a twelve-string guitar: one whose six courses are the guitar's six strings, each doubled (an
 * octave up on the four lowest), so that it shares the guitar's tunings and its chord shapes, and only the tuner, which
 * hears the second string of each course as well, tells the two apart.
 */
internal val UserPreferences.Instrument.isTwelveString get() = this == UserPreferences.Instrument.GUITAR_TWELVE_STRING

/** How the instrument strings its tuning, which the tuner listens for: the mandolin's pairs, the twelve-string's courses. */
internal val UserPreferences.Instrument.courses
    get() = when (this) {
        UserPreferences.Instrument.MANDOLIN -> StringCourses.UNISON_PAIRS
        UserPreferences.Instrument.GUITAR_TWELVE_STRING -> StringCourses.TWELVE_STRING
        else -> StringCourses.SINGLE
    }

/** Whether [this] is the standard tuning of the instrument, or instruments, it is a tuning of. */
internal val InstrumentTuning.isStandard get() = UserPreferences.Instrument.entries.any { it.tunings.firstOrNull() == this }

/**
 * The tuning [UserPreferences.tunings] names for [instrument], held to that instrument's own: its standard tuning where
 * the map names none, one this version does not know, or one of another instrument. Null for an instrument without
 * tunings.
 */
internal fun UserPreferences.tuningOf(instrument: UserPreferences.Instrument): InstrumentTuning? {
    val offered = instrument.tunings
    return offered.firstOrNull { it.id == tunings[instrument.id] } ?: offered.firstOrNull()
}

/** What the tuner listens for, null where the instrument has no strings to tune and the tuner reads chromatically. */
internal val UserPreferences.instrumentTuning get() = tuningOf(instrument)

/**
 * The tuning the chord diagrams are drawn for: [instrumentTuning]'s, the keyboard's own for the keyboard, and null for
 * an instrument without shapes, which draws no diagrams.
 */
internal fun UserPreferences.chordTuning(): ChordTuning? {
    val chordInstrument = instrument.chordInstrument ?: return null
    return instrumentTuning?.let { ChordTuning.fromId(it.id) } ?: ChordTuning.standard(chordInstrument)
}

/** The player's own shapes for the chords of [tuning], by chord, where they picked one over the app's first. */
internal fun UserPreferences.storedChordShapes(tuning: ChordTuning) = chordVoicings[tuning.voicingKey].orEmpty()

internal fun UserPreferences.withInstrument(instrument: UserPreferences.Instrument) = copy(instrument = instrument)

/**
 * [this] with the instrument in the player's hands tuned to [tuning], which a standard tuning leaves no entry for. A
 * tuning that is not one of that instrument's changes nothing: a tuning names no instrument, since the guitar's are the
 * twelve-string's too, so it is only ever picked for the one its list was offered for.
 */
internal fun UserPreferences.withTuning(tuning: InstrumentTuning) = when (tuning) {
    !in instrument.tunings -> this
    instrument.tunings.first() -> copy(tunings = tunings - instrument.id)
    else -> copy(tunings = tunings + (instrument.id to tuning.id))
}
