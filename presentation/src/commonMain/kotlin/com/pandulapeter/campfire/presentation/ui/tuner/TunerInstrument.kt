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

import com.pandulapeter.campfire.tuner.api.model.InstrumentTuning

/**
 * The instruments [InstrumentChoice] offers, in the order of its chips, and the tunings of each, in the order of the
 * chips under it, the standard one first. A guitar or a bass with more strings is an instrument of its own, since a
 * tuning of one is never a tuning of the other. Chromatic tuning is no instrument at all, null where one is asked for.
 */
internal enum class TunerInstrument(
    val tunings: List<InstrumentTuning>,
) {
    GUITAR(
        listOf(
            InstrumentTuning.GUITAR,
            InstrumentTuning.GUITAR_DROP_D,
            InstrumentTuning.GUITAR_HALF_STEP_DOWN,
            InstrumentTuning.GUITAR_FULL_STEP_DOWN,
            InstrumentTuning.GUITAR_DROP_C,
            InstrumentTuning.GUITAR_OPEN_G,
            InstrumentTuning.GUITAR_OPEN_D,
            InstrumentTuning.GUITAR_DADGAD,
        ),
    ),
    SEVEN_STRING_GUITAR(listOf(InstrumentTuning.GUITAR_SEVEN_STRING, InstrumentTuning.GUITAR_SEVEN_STRING_DROP_A)),
    EIGHT_STRING_GUITAR(listOf(InstrumentTuning.GUITAR_EIGHT_STRING, InstrumentTuning.GUITAR_EIGHT_STRING_DROP_E)),
    BASS(
        listOf(
            InstrumentTuning.BASS,
            InstrumentTuning.BASS_DROP_D,
            InstrumentTuning.BASS_HALF_STEP_DOWN,
            InstrumentTuning.BASS_FULL_STEP_DOWN,
        ),
    ),
    FIVE_STRING_BASS(listOf(InstrumentTuning.BASS_FIVE_STRING, InstrumentTuning.BASS_FIVE_STRING_HIGH_C)),
    UKULELE(listOf(InstrumentTuning.UKULELE, InstrumentTuning.UKULELE_LOW_G, InstrumentTuning.UKULELE_BARITONE)),
    BANJO(listOf(InstrumentTuning.BANJO, InstrumentTuning.BANJO_DOUBLE_C)),
    VIOLIN(listOf(InstrumentTuning.VIOLIN)),
    MANDOLIN(listOf(InstrumentTuning.MANDOLIN)),
}

/** The instrument [tuning] is a tuning of, null for chromatic tuning. */
internal fun instrumentOf(tuning: InstrumentTuning?) = TunerInstrument.entries.firstOrNull { tuning in it.tunings }
