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
 * One instrument and the tunings [InstrumentPicker] offers for it as chips, under the instrument's name where its
 * [InstrumentFamily] holds more than one. A guitar or a bass with more strings is an instrument of its own, since a
 * tuning of one is never a tuning of the other.
 */
internal enum class TuningGroup(
    val tunings: List<InstrumentTuning>,
) {
    SIX_STRING_GUITAR(
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
    FOUR_STRING_BASS(
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
