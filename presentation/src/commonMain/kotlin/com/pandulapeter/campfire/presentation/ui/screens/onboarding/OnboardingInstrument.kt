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

import com.pandulapeter.campfire.chordpro.model.ChordTuning
import com.pandulapeter.campfire.data.model.domain.UserPreferences.ChordInstrument
import com.pandulapeter.campfire.presentation.ui.chords.toChordInstrument
import com.pandulapeter.campfire.presentation.ui.tuner.TunerInstrument
import com.pandulapeter.campfire.presentation.ui.tuner.instrumentOf
import com.pandulapeter.campfire.tuner.api.model.InstrumentTuning

/**
 * The answers to the onboarding's "What do you play?", each standing for the two settings an instrument and its tuning
 * decide: what the chord diagrams are drawn for, and what the tuner reads against. [OTHER] is an answer too, the one for
 * an instrument that is not listed (or a voice): no diagrams, and the tuner reading the nearest note. Not a setting of
 * its own: what is picked is written into those two, and what the page shows as picked is read back out of them
 * ([onboardingInstrumentOf]), so that going through the onboarding again starts from what the app is set to now,
 * whichever screen it was set on.
 *
 * @param tunerInstruments The tuner's instruments this one covers, the first one's standard tuning being what picking it
 *   tunes to; empty for one the tuner reads chromatically.
 * @param isOnlyForTuner Whether the tuner is all this answer decides, the diagrams being none for it as for Something
 *   else: it is only offered with the tuner on ([onboardingInstruments]).
 */
internal enum class OnboardingInstrument(
    private val chordInstrument: ChordInstrument,
    val tunerInstruments: List<TunerInstrument>,
    val isOnlyForTuner: Boolean = false,
) {
    GUITAR(
        chordInstrument = ChordInstrument.GUITAR,
        tunerInstruments = listOf(TunerInstrument.GUITAR, TunerInstrument.SEVEN_STRING_GUITAR, TunerInstrument.EIGHT_STRING_GUITAR),
    ),
    UKULELE(
        chordInstrument = ChordInstrument.UKULELE,
        tunerInstruments = listOf(TunerInstrument.UKULELE),
    ),
    MANDOLIN(
        chordInstrument = ChordInstrument.MANDOLIN,
        tunerInstruments = listOf(TunerInstrument.MANDOLIN),
    ),
    KEYBOARD(
        chordInstrument = ChordInstrument.KEYBOARD,
        tunerInstruments = emptyList(),
    ),
    BASS(
        chordInstrument = ChordInstrument.OTHER,
        tunerInstruments = listOf(TunerInstrument.BASS, TunerInstrument.FIVE_STRING_BASS),
        isOnlyForTuner = true,
    ),
    BANJO(
        chordInstrument = ChordInstrument.OTHER,
        tunerInstruments = listOf(TunerInstrument.BANJO),
        isOnlyForTuner = true,
    ),
    VIOLIN(
        chordInstrument = ChordInstrument.OTHER,
        tunerInstruments = listOf(TunerInstrument.VIOLIN),
        isOnlyForTuner = true,
    ),
    OTHER(
        chordInstrument = ChordInstrument.OTHER,
        tunerInstruments = emptyList(),
    ),
    ;

    /** Whether the chord diagrams are drawn for this instrument in some tuning, so that a tuning without them is worth a word. */
    val hasDiagrams get() = chordInstrument != ChordInstrument.OTHER

    /**
     * The tuning the chord diagrams are drawn in for this instrument tuned to [tuning], the tuner's: the diagrams' tuning
     * of the same id, the keyboard's for an instrument that is not tuned at all, and null where the diagrams know no such
     * tuning (a seven- or eight-string guitar), since with the strings tuned any other way they would show the wrong
     * notes.
     */
    fun chordTuningFor(tuning: InstrumentTuning?): ChordTuning? = when (chordInstrument) {
        ChordInstrument.OTHER -> null
        ChordInstrument.KEYBOARD -> ChordTuning.KEYBOARD.takeIf { tuning == null }
        else -> tuning?.let { ChordTuning.fromId(it.id) }?.takeIf { it.instrument.id == chordInstrument.id }
    }

    /** Whether the chord diagrams are drawn for this instrument tuned to [tuning]. */
    fun hasDiagramsIn(tuning: InstrumentTuning?) = chordTuningFor(tuning) != null

    /** What the diagrams are drawn for once this is picked tuned to [tuning]: none where the shapes would be wrong for it. */
    fun chordInstrumentFor(tuning: InstrumentTuning?) = if (hasDiagramsIn(tuning)) chordInstrument else ChordInstrument.OTHER

    /**
     * The tuning written as the diagrams' for this instrument once it is picked tuned to [tuning]: [chordTuningFor] where
     * the diagrams know it, and otherwise the instrument's standard one, which is drawn for nothing while the diagrams'
     * instrument is Other ([chordInstrumentFor]); null for an answer that names no instrument the diagrams know.
     */
    fun chordTuningWrittenFor(tuning: InstrumentTuning?): ChordTuning? = when (chordInstrument) {
        ChordInstrument.OTHER, ChordInstrument.KEYBOARD -> null
        else -> chordTuningFor(tuning) ?: chordInstrument.toChordInstrument()?.let(ChordTuning::standard)
    }

    /**
     * What the tuner is set to once this is picked over [current]: [current] itself where it already is a tuning of this
     * instrument, so that a guitarist in drop D who goes through the onboarding again stays in drop D, and otherwise the
     * standard tuning of the instrument, or chromatic (null) for one the tuner has no strings for.
     */
    fun tuningFor(current: InstrumentTuning?): InstrumentTuning? =
        if (current != null && instrumentOf(current) in tunerInstruments) current else tunerInstruments.firstOrNull()?.tunings?.first()

    /**
     * The tunings offered once this is picked: every tuning of its first tuner instrument, and the standard one of each
     * of the others (the guitar's seven and eight strings), which would otherwise bury the guitar's own under three
     * lists of drop tunings. [current] is added where it is a tuning of this instrument that is not among them, so that
     * what the tuner is set to is always shown picked.
     */
    fun tuningsOffered(current: InstrumentTuning?): List<InstrumentTuning> {
        val first = tunerInstruments.firstOrNull() ?: return emptyList()
        val offered = first.tunings + tunerInstruments.drop(1).map { it.tunings.first() }
        return if (current != null && current !in offered && instrumentOf(current) in tunerInstruments) offered + current else offered
    }
}

/**
 * The answer the settings add up to, null where they add up to none of them: a fresh installation (guitar diagrams over a
 * chromatic tuner) is one, and so is a mix set by hand, such as ukulele diagrams with a guitar tuner, or guitar diagrams
 * over a drop D the shapes are wrong for. The page then starts with nothing picked rather than with a guess, which picking
 * anything would silently overwrite.
 */
internal fun onboardingInstrumentOf(chordInstrument: ChordInstrument, tuning: InstrumentTuning?) = OnboardingInstrument.entries.firstOrNull {
    val isTunedFor = if (it.tunerInstruments.isEmpty()) tuning == null else instrumentOf(tuning) in it.tunerInstruments
    isTunedFor && chordInstrument == it.chordInstrumentFor(tuning)
}

/**
 * The answers worth offering: with the tuner off the answer only decides the chord diagrams, which are drawn for a guitar,
 * a ukulele, a mandolin and a keyboard, so every other instrument would be Something else by another name.
 */
internal fun onboardingInstruments(isTunerEnabled: Boolean) = OnboardingInstrument.entries.filter { isTunerEnabled || !it.isOnlyForTuner }
