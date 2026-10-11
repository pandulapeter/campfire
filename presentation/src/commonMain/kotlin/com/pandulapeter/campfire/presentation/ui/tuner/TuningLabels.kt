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
import com.pandulapeter.campfire.chordpro.ChordNotation
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
import com.pandulapeter.campfire.presentation.resources.tuner_tuning_double
import com.pandulapeter.campfire.presentation.resources.tuner_tuning_double_drop
import com.pandulapeter.campfire.presentation.resources.tuner_tuning_drop
import com.pandulapeter.campfire.presentation.resources.tuner_tuning_full_step_down
import com.pandulapeter.campfire.presentation.resources.tuner_tuning_half_step_down
import com.pandulapeter.campfire.presentation.resources.tuner_tuning_high
import com.pandulapeter.campfire.presentation.resources.tuner_tuning_key
import com.pandulapeter.campfire.presentation.resources.tuner_tuning_low
import com.pandulapeter.campfire.presentation.resources.tuner_tuning_modal
import com.pandulapeter.campfire.presentation.resources.tuner_tuning_open
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

/**
 * The name of [tuning] on its chip, its instrument named above it: "Drop D", or "Standard" for the one most are strung in.
 * Its note is written in the reader's chord [notation] rather than in the app's language, since it is the same note the
 * strings and the chords on a page are named by.
 */
@Composable
internal fun tuningLabel(tuning: InstrumentTuning, notation: ChordNotation) = when (val name = tuningNameOf(tuning)) {
    TuningName.Plain.STANDARD -> stringResource(Res.string.tuner_tuning_standard)
    TuningName.Plain.HALF_STEP_DOWN -> stringResource(Res.string.tuner_tuning_half_step_down)
    TuningName.Plain.FULL_STEP_DOWN -> stringResource(Res.string.tuner_tuning_full_step_down)
    TuningName.Plain.BARITONE -> stringResource(Res.string.tuner_tuning_baritone)
    is TuningName.Noted -> stringResource(
        when (name.kind) {
            TuningName.Noted.Kind.DROP -> Res.string.tuner_tuning_drop
            TuningName.Noted.Kind.DOUBLE_DROP -> Res.string.tuner_tuning_double_drop
            TuningName.Noted.Kind.OPEN -> Res.string.tuner_tuning_open
            TuningName.Noted.Kind.HIGH -> Res.string.tuner_tuning_high
            TuningName.Noted.Kind.LOW -> Res.string.tuner_tuning_low
            TuningName.Noted.Kind.DOUBLE -> Res.string.tuner_tuning_double
            TuningName.Noted.Kind.KEY -> Res.string.tuner_tuning_key
            TuningName.Noted.Kind.MODAL -> Res.string.tuner_tuning_modal
        },
        noteName(name.note, notation),
    )
    is TuningName.Spelled -> spelledTuning(name.strings, notation)
}
