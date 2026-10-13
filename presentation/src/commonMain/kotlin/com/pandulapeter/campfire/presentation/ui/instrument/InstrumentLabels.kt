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

import androidx.compose.runtime.Composable
import com.pandulapeter.campfire.chordpro.ChordNotation
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.instrument_banjo
import com.pandulapeter.campfire.presentation.resources.instrument_bass
import com.pandulapeter.campfire.presentation.resources.instrument_bass_strings
import com.pandulapeter.campfire.presentation.resources.instrument_guitar
import com.pandulapeter.campfire.presentation.resources.instrument_guitar_strings
import com.pandulapeter.campfire.presentation.resources.instrument_keyboard
import com.pandulapeter.campfire.presentation.resources.instrument_keyboard_description
import com.pandulapeter.campfire.presentation.resources.instrument_mandolin
import com.pandulapeter.campfire.presentation.resources.instrument_no_chord_diagrams
import com.pandulapeter.campfire.presentation.resources.instrument_other
import com.pandulapeter.campfire.presentation.resources.instrument_other_description
import com.pandulapeter.campfire.presentation.resources.instrument_strings_banjo
import com.pandulapeter.campfire.presentation.resources.instrument_strings_eight
import com.pandulapeter.campfire.presentation.resources.instrument_strings_five
import com.pandulapeter.campfire.presentation.resources.instrument_strings_four
import com.pandulapeter.campfire.presentation.resources.instrument_strings_pairs
import com.pandulapeter.campfire.presentation.resources.instrument_strings_seven
import com.pandulapeter.campfire.presentation.resources.instrument_strings_six_pairs
import com.pandulapeter.campfire.presentation.resources.instrument_strings_six
import com.pandulapeter.campfire.presentation.resources.instrument_ukulele
import com.pandulapeter.campfire.presentation.resources.instrument_violin
import com.pandulapeter.campfire.presentation.ui.tuner.noteName
import com.pandulapeter.campfire.tuner.api.model.InstrumentTuning
import org.jetbrains.compose.resources.StringResource

/** The one name each instrument goes by, wherever it is picked or shown. */
@Composable
internal fun instrumentLabel(instrument: UserPreferences.Instrument) = when (instrument) {
    UserPreferences.Instrument.GUITAR -> stringResource(Res.string.instrument_guitar)
    UserPreferences.Instrument.GUITAR_SEVEN_STRING -> stringResource(Res.string.instrument_guitar_strings, 7)
    UserPreferences.Instrument.GUITAR_EIGHT_STRING -> stringResource(Res.string.instrument_guitar_strings, 8)
    UserPreferences.Instrument.GUITAR_TWELVE_STRING -> stringResource(Res.string.instrument_guitar_strings, 12)
    UserPreferences.Instrument.BASS -> stringResource(Res.string.instrument_bass)
    UserPreferences.Instrument.BASS_FIVE_STRING -> stringResource(Res.string.instrument_bass_strings, 5)
    UserPreferences.Instrument.UKULELE -> stringResource(Res.string.instrument_ukulele)
    UserPreferences.Instrument.BANJO -> stringResource(Res.string.instrument_banjo)
    UserPreferences.Instrument.MANDOLIN -> stringResource(Res.string.instrument_mandolin)
    UserPreferences.Instrument.VIOLIN -> stringResource(Res.string.instrument_violin)
    UserPreferences.Instrument.KEYBOARD -> stringResource(Res.string.instrument_keyboard)
    UserPreferences.Instrument.OTHER -> stringResource(Res.string.instrument_other)
}

/**
 * What the instrument list says under the instrument's name: how many strings it has ("Six strings", the banjo's short
 * one and the pairs of the mandolin and the twelve-string guitar worth a word of their own), and after that that there are no diagrams for an instrument
 * without shapes, so that the player knows before picking it. Nothing about a tuning, which is a choice of its own
 * ([tuningStringsLabel]). The keyboard and Other, which have no strings, say what they do instead.
 */
@Composable
internal fun instrumentDescription(instrument: UserPreferences.Instrument): String {
    val stringCount = instrument.tunings.firstOrNull()?.strings?.size
        ?: return stringResource(
            if (instrument == UserPreferences.Instrument.KEYBOARD) Res.string.instrument_keyboard_description else Res.string.instrument_other_description,
        )
    val strings = stringResource(stringsSentenceOf(instrument, stringCount))
    return if (instrument.hasDiagrams) strings else stringResource(Res.string.instrument_no_chord_diagrams, strings)
}

/**
 * The sentence naming [count] strings: the banjo's short one and the pairs of the mandolin and the twelve-string guitar
 * are worth a word of their own, the twelve-string's counted by its six courses, since [count] is its guitar tuning's.
 */
private fun stringsSentenceOf(instrument: UserPreferences.Instrument, count: Int): StringResource = when {
    instrument == UserPreferences.Instrument.BANJO -> Res.string.instrument_strings_banjo
    instrument == UserPreferences.Instrument.MANDOLIN -> Res.string.instrument_strings_pairs
    instrument.isTwelveString -> Res.string.instrument_strings_six_pairs
    count <= 4 -> Res.string.instrument_strings_four
    count == 5 -> Res.string.instrument_strings_five
    count == 6 -> Res.string.instrument_strings_six
    count == 7 -> Res.string.instrument_strings_seven
    else -> Res.string.instrument_strings_eight
}

/**
 * The open strings of [tuning] in a chord chart's order, which is what the tuning list says under each tuning's name
 * ("D A D G B E"): in the chord [notation] rather than the app's language, as the diagrams' chords are.
 */
internal fun tuningStringsLabel(tuning: InstrumentTuning, notation: ChordNotation) = tuning.strings.joinToString(" ") { noteName(it, notation) }
