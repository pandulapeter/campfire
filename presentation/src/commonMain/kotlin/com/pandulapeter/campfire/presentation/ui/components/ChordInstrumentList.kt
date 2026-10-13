/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pandulapeter.campfire.chordpro.ChordNotation
import com.pandulapeter.campfire.chordpro.model.ChordInstrument
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.settings_chord_instrument_guitar
import com.pandulapeter.campfire.presentation.resources.settings_chord_instrument_guitar_description
import com.pandulapeter.campfire.presentation.resources.settings_chord_instrument_keyboard
import com.pandulapeter.campfire.presentation.resources.settings_chord_instrument_keyboard_description
import com.pandulapeter.campfire.presentation.resources.settings_chord_instrument_mandolin
import com.pandulapeter.campfire.presentation.resources.settings_chord_instrument_mandolin_description
import com.pandulapeter.campfire.presentation.resources.settings_chord_instrument_other
import com.pandulapeter.campfire.presentation.resources.settings_chord_instrument_other_description
import com.pandulapeter.campfire.presentation.resources.settings_chord_instrument_ukulele
import com.pandulapeter.campfire.presentation.resources.settings_chord_instrument_ukulele_description
import com.pandulapeter.campfire.presentation.ui.chords.chordTuningOf
import com.pandulapeter.campfire.presentation.ui.tuner.noteName

/**
 * The instruments the chord diagrams can be drawn for, as a list rather than a segmented control: the strings under
 * each name are what say that the shapes are for that instrument in the tuning [chordTunings] names for it (standard
 * where it names none), so a player whose instrument is not one of these picks Other rather than reading diagrams their
 * strings do not match. The strings are named in the chord [notation], as the diagrams' chords are, rather than in the
 * app's language. Shared by Settings and by the sheet the song details screen's Chords section opens.
 *
 * @param isOtherShown Whether Other is offered, which draws no diagram: the song details screen's sheet leaves it out,
 * since picking it there would take away the very section it was opened from, along with the way back.
 */
@Composable
internal fun ChordInstrumentList(
    modifier: Modifier = Modifier,
    selected: UserPreferences.ChordInstrument?,
    chordTunings: Map<String, String>,
    notation: ChordNotation,
    isOtherShown: Boolean = true,
    isEnabled: Boolean = true,
    onSelected: (UserPreferences.ChordInstrument) -> Unit,
) = Column(modifier = modifier.selectableGroup()) {
    listOfNotNull(
        Triple(
            UserPreferences.ChordInstrument.GUITAR,
            stringResource(Res.string.settings_chord_instrument_guitar),
            stringResource(Res.string.settings_chord_instrument_guitar_description, stringNames(ChordInstrument.GUITAR, chordTunings, notation)),
        ),
        Triple(
            UserPreferences.ChordInstrument.UKULELE,
            stringResource(Res.string.settings_chord_instrument_ukulele),
            stringResource(Res.string.settings_chord_instrument_ukulele_description, stringNames(ChordInstrument.UKULELE, chordTunings, notation)),
        ),
        Triple(
            UserPreferences.ChordInstrument.MANDOLIN,
            stringResource(Res.string.settings_chord_instrument_mandolin),
            stringResource(Res.string.settings_chord_instrument_mandolin_description, stringNames(ChordInstrument.MANDOLIN, chordTunings, notation)),
        ),
        Triple(
            UserPreferences.ChordInstrument.KEYBOARD,
            stringResource(Res.string.settings_chord_instrument_keyboard),
            stringResource(Res.string.settings_chord_instrument_keyboard_description),
        ),
        if (isOtherShown) {
            Triple(
                UserPreferences.ChordInstrument.OTHER,
                stringResource(Res.string.settings_chord_instrument_other),
                stringResource(Res.string.settings_chord_instrument_other_description),
            )
        } else {
            null
        },
    ).forEach { (instrument, title, description) ->
        RadioListItem(
            title = title,
            description = description,
            isSelected = selected == instrument,
            isEnabled = isEnabled,
            onSelected = { onSelected(instrument) },
        )
    }
}

/** The open strings of [instrument] in its tuning, in a chord chart's order, written in [notation]: `E A D G B E`. */
private fun stringNames(instrument: ChordInstrument, chordTunings: Map<String, String>, notation: ChordNotation) =
    chordTuningOf(instrument, chordTunings).strings.joinToString(" ") { noteName(it, notation) }
