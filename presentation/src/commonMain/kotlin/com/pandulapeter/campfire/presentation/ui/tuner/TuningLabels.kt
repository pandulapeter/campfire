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

import androidx.compose.runtime.Composable
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.tuner_instrument_banjo
import com.pandulapeter.campfire.presentation.resources.tuner_instrument_bass
import com.pandulapeter.campfire.presentation.resources.tuner_instrument_bass_strings
import com.pandulapeter.campfire.presentation.resources.tuner_instrument_chromatic
import com.pandulapeter.campfire.presentation.resources.tuner_instrument_guitar
import com.pandulapeter.campfire.presentation.resources.tuner_instrument_guitar_strings
import com.pandulapeter.campfire.presentation.resources.tuner_instrument_mandolin
import com.pandulapeter.campfire.presentation.resources.tuner_instrument_ukulele
import com.pandulapeter.campfire.presentation.resources.tuner_instrument_violin
import com.pandulapeter.campfire.presentation.resources.tuner_tuning_baritone
import com.pandulapeter.campfire.presentation.resources.tuner_tuning_dadgad
import com.pandulapeter.campfire.presentation.resources.tuner_tuning_double_c
import com.pandulapeter.campfire.presentation.resources.tuner_tuning_drop_a
import com.pandulapeter.campfire.presentation.resources.tuner_tuning_drop_c
import com.pandulapeter.campfire.presentation.resources.tuner_tuning_drop_d
import com.pandulapeter.campfire.presentation.resources.tuner_tuning_drop_e
import com.pandulapeter.campfire.presentation.resources.tuner_tuning_full_step_down
import com.pandulapeter.campfire.presentation.resources.tuner_tuning_half_step_down
import com.pandulapeter.campfire.presentation.resources.tuner_tuning_high_c
import com.pandulapeter.campfire.presentation.resources.tuner_tuning_low_g
import com.pandulapeter.campfire.presentation.resources.tuner_tuning_open_d
import com.pandulapeter.campfire.presentation.resources.tuner_tuning_open_g
import com.pandulapeter.campfire.presentation.resources.tuner_tuning_standard
import com.pandulapeter.campfire.tuner.api.model.InstrumentTuning

/** The name of [instrument] on its chip, chromatic tuning's for null: "7-string guitar", "Banjo". */
@Composable
internal fun instrumentLabel(instrument: TunerInstrument?) = when (instrument) {
    null -> stringResource(Res.string.tuner_instrument_chromatic)
    TunerInstrument.GUITAR -> stringResource(Res.string.tuner_instrument_guitar)
    TunerInstrument.SEVEN_STRING_GUITAR -> stringResource(Res.string.tuner_instrument_guitar_strings, 7)
    TunerInstrument.EIGHT_STRING_GUITAR -> stringResource(Res.string.tuner_instrument_guitar_strings, 8)
    TunerInstrument.BASS -> stringResource(Res.string.tuner_instrument_bass)
    TunerInstrument.FIVE_STRING_BASS -> stringResource(Res.string.tuner_instrument_bass_strings, 5)
    TunerInstrument.UKULELE -> stringResource(Res.string.tuner_instrument_ukulele)
    TunerInstrument.BANJO -> stringResource(Res.string.tuner_instrument_banjo)
    TunerInstrument.VIOLIN -> stringResource(Res.string.tuner_instrument_violin)
    TunerInstrument.MANDOLIN -> stringResource(Res.string.tuner_instrument_mandolin)
}

/** The name of [tuning] on its chip, its instrument named above it: "Drop D", or "Standard" for the one most are strung in. */
@Composable
internal fun tuningLabel(tuning: InstrumentTuning) = stringResource(
    when (tuning) {
        InstrumentTuning.GUITAR,
        InstrumentTuning.GUITAR_SEVEN_STRING,
        InstrumentTuning.GUITAR_EIGHT_STRING,
        InstrumentTuning.BASS,
        InstrumentTuning.BASS_FIVE_STRING,
        InstrumentTuning.UKULELE,
        InstrumentTuning.VIOLIN,
        InstrumentTuning.MANDOLIN -> Res.string.tuner_tuning_standard
        InstrumentTuning.GUITAR_DROP_D, InstrumentTuning.BASS_DROP_D -> Res.string.tuner_tuning_drop_d
        InstrumentTuning.GUITAR_HALF_STEP_DOWN, InstrumentTuning.BASS_HALF_STEP_DOWN -> Res.string.tuner_tuning_half_step_down
        InstrumentTuning.GUITAR_FULL_STEP_DOWN, InstrumentTuning.BASS_FULL_STEP_DOWN -> Res.string.tuner_tuning_full_step_down
        InstrumentTuning.GUITAR_DROP_C -> Res.string.tuner_tuning_drop_c
        InstrumentTuning.GUITAR_OPEN_G, InstrumentTuning.BANJO -> Res.string.tuner_tuning_open_g
        InstrumentTuning.GUITAR_OPEN_D -> Res.string.tuner_tuning_open_d
        InstrumentTuning.GUITAR_DADGAD -> Res.string.tuner_tuning_dadgad
        InstrumentTuning.GUITAR_SEVEN_STRING_DROP_A -> Res.string.tuner_tuning_drop_a
        InstrumentTuning.GUITAR_EIGHT_STRING_DROP_E -> Res.string.tuner_tuning_drop_e
        InstrumentTuning.BASS_FIVE_STRING_HIGH_C -> Res.string.tuner_tuning_high_c
        InstrumentTuning.UKULELE_LOW_G -> Res.string.tuner_tuning_low_g
        InstrumentTuning.UKULELE_BARITONE -> Res.string.tuner_tuning_baritone
        InstrumentTuning.BANJO_DOUBLE_C -> Res.string.tuner_tuning_double_c
    }
)
