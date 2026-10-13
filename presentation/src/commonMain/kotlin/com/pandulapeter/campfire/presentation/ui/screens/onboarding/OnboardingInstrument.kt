/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.onboarding

import com.pandulapeter.campfire.data.model.domain.UserPreferences.ChordInstrument
import com.pandulapeter.campfire.presentation.ui.tuner.TunerInstrument
import com.pandulapeter.campfire.presentation.ui.tuner.instrumentOf
import com.pandulapeter.campfire.tuner.api.model.InstrumentTuning

/**
 * The answers to the onboarding's "What do you play?", each standing for the two settings an instrument decides: what the
 * chord diagrams are drawn for, and what the tuner reads against. [OTHER] is an answer too, the one for an instrument
 * that is not listed (or a voice): no diagrams, and the tuner reading the nearest note. Not a setting of its own: what is picked is written into
 * those two, and what the page shows as picked is read back out of them ([onboardingInstrumentOf]), so that going through
 * the onboarding again starts from what the app is set to now, whichever screen it was set on.
 *
 * @param tunerInstruments The tuner's instruments this one covers, the first one's standard tuning being what picking it
 *   tunes to; empty for one the tuner reads chromatically.
 * @param isOnlyForTuner Whether the tuner is all this answer decides, the diagrams being none for it as for Something
 *   else: it is only offered with the tuner on ([onboardingInstruments]).
 */
internal enum class OnboardingInstrument(
    val chordInstrument: ChordInstrument,
    val tunerInstruments: List<TunerInstrument>,
    val isOnlyForTuner: Boolean = false,
) {
    GUITAR(ChordInstrument.GUITAR, listOf(TunerInstrument.GUITAR, TunerInstrument.SEVEN_STRING_GUITAR, TunerInstrument.EIGHT_STRING_GUITAR)),
    UKULELE(ChordInstrument.UKULELE, listOf(TunerInstrument.UKULELE)),
    MANDOLIN(ChordInstrument.MANDOLIN, listOf(TunerInstrument.MANDOLIN)),
    KEYBOARD(ChordInstrument.KEYBOARD, emptyList()),
    BASS(ChordInstrument.OTHER, listOf(TunerInstrument.BASS, TunerInstrument.FIVE_STRING_BASS), isOnlyForTuner = true),
    BANJO(ChordInstrument.OTHER, listOf(TunerInstrument.BANJO), isOnlyForTuner = true),
    VIOLIN(ChordInstrument.OTHER, listOf(TunerInstrument.VIOLIN), isOnlyForTuner = true),
    OTHER(ChordInstrument.OTHER, emptyList()),
    ;

    /**
     * What the tuner is set to once this is picked over [current]: [current] itself where it already is a tuning of this
     * instrument, so that a guitarist in drop D who goes through the onboarding again stays in drop D, and otherwise the
     * standard tuning of the instrument, or chromatic (null) for one the tuner has no strings for.
     */
    fun tuningFor(current: InstrumentTuning?): InstrumentTuning? =
        if (current != null && instrumentOf(current) in tunerInstruments) current else tunerInstruments.firstOrNull()?.tunings?.first()
}

/**
 * The answer the settings add up to, null where they add up to none of them: a fresh installation (guitar diagrams over a
 * chromatic tuner) is one, and so is a mix set by hand, such as ukulele diagrams with a guitar tuner. The page then starts
 * with nothing picked rather than with a guess, which picking anything would silently overwrite.
 */
internal fun onboardingInstrumentOf(chordInstrument: ChordInstrument, tuning: InstrumentTuning?) = OnboardingInstrument.entries.firstOrNull {
    it.chordInstrument == chordInstrument && if (it.tunerInstruments.isEmpty()) tuning == null else instrumentOf(tuning) in it.tunerInstruments
}

/**
 * The answers worth offering: with the tuner off the answer only decides the chord diagrams, which are drawn for a guitar,
 * a ukulele and a keyboard, so every other instrument would be Something else by another name.
 */
internal fun onboardingInstruments(isTunerEnabled: Boolean) = OnboardingInstrument.entries.filter { isTunerEnabled || !it.isOnlyForTuner }
