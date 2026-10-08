/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.settings_accidentals
import com.pandulapeter.campfire.presentation.resources.settings_accidentals_description
import com.pandulapeter.campfire.presentation.resources.settings_accidentals_flats
import com.pandulapeter.campfire.presentation.resources.settings_accidentals_original
import com.pandulapeter.campfire.presentation.resources.settings_accidentals_sharps
import com.pandulapeter.campfire.presentation.resources.settings_chord_instrument
import com.pandulapeter.campfire.presentation.resources.settings_chord_instrument_description
import com.pandulapeter.campfire.presentation.resources.settings_chord_instrument_guitar
import com.pandulapeter.campfire.presentation.resources.settings_chord_instrument_keyboard
import com.pandulapeter.campfire.presentation.resources.settings_chord_instrument_ukulele
import com.pandulapeter.campfire.presentation.resources.settings_notation
import com.pandulapeter.campfire.presentation.resources.settings_notation_description
import com.pandulapeter.campfire.presentation.resources.settings_notation_german
import com.pandulapeter.campfire.presentation.resources.settings_notation_german_description
import com.pandulapeter.campfire.presentation.resources.settings_notation_latin
import com.pandulapeter.campfire.presentation.resources.settings_notation_latin_description
import com.pandulapeter.campfire.presentation.resources.settings_notation_nashville
import com.pandulapeter.campfire.presentation.resources.settings_notation_nashville_description
import com.pandulapeter.campfire.presentation.resources.settings_notation_roman
import com.pandulapeter.campfire.presentation.resources.settings_notation_roman_description
import com.pandulapeter.campfire.presentation.resources.settings_notation_standard
import com.pandulapeter.campfire.presentation.resources.settings_notation_standard_description
import com.pandulapeter.campfire.presentation.resources.settings_number_sections
import com.pandulapeter.campfire.presentation.resources.settings_number_sections_description
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.RadioListItem
import com.pandulapeter.campfire.presentation.ui.components.SegmentedChoice
import com.pandulapeter.campfire.presentation.ui.components.SettingsSubsection
import com.pandulapeter.campfire.presentation.ui.components.SwitchListItem

@Composable
internal fun SongDisplaySection(
    viewModel: CampfireViewModel,
    userPreferences: UserPreferences?,
) = SettingsSection {
    SwitchListItem(
        title = stringResource(Res.string.settings_number_sections),
        description = stringResource(Res.string.settings_number_sections_description),
        isChecked = userPreferences?.shouldNumberSections == true,
        onCheckedChange = viewModel::setSectionNumberingEnabled,
    )
    // Both of these only decide how a chord is written, so with the chords switched off they have nothing to say. They
    // stay in the section rather than disappearing from it: what they are set to is still what the chords will look
    // like as soon as they are shown again. The accidentals stay enabled under a numbering, since they still spell the
    // key it counts from.
    val isChordSpellingEnabled = userPreferences?.areChordsEnabled != false
    NotationChoice(
        selected = userPreferences?.chordSpelling?.notation,
        isEnabled = isChordSpellingEnabled,
        onSelected = viewModel::setNotation,
    )
    SettingsSubsection(
        title = stringResource(Res.string.settings_accidentals),
        description = stringResource(Res.string.settings_accidentals_description),
        isEnabled = isChordSpellingEnabled,
    ) {
        SegmentedChoice(
            options = listOf(
                UserPreferences.Accidentals.ORIGINAL to stringResource(Res.string.settings_accidentals_original),
                UserPreferences.Accidentals.FLATS to stringResource(Res.string.settings_accidentals_flats),
                UserPreferences.Accidentals.SHARPS to stringResource(Res.string.settings_accidentals_sharps),
            ),
            selected = userPreferences?.chordSpelling?.accidentals,
            isEnabled = isChordSpellingEnabled,
            onSelected = viewModel::setAccidentals,
        )
    }
    val isChordInstrumentEnabled = isChordSpellingEnabled && userPreferences?.areChordDiagramsEnabled != false
    SettingsSubsection(
        title = stringResource(Res.string.settings_chord_instrument),
        description = stringResource(Res.string.settings_chord_instrument_description),
        isEnabled = isChordInstrumentEnabled,
    ) {
        SegmentedChoice(
            options = listOf(
                UserPreferences.ChordInstrument.GUITAR to stringResource(Res.string.settings_chord_instrument_guitar),
                UserPreferences.ChordInstrument.UKULELE to stringResource(Res.string.settings_chord_instrument_ukulele),
                UserPreferences.ChordInstrument.KEYBOARD to stringResource(Res.string.settings_chord_instrument_keyboard),
            ),
            selected = userPreferences?.chordInstrument,
            isEnabled = isChordInstrumentEnabled,
            onSelected = viewModel::setChordInstrument,
        )
    }
}

/**
 * The notation chords are written in, as a list rather than a segmented control: five names with an example under each
 * do not fit a row of segments at the width of a phone. Each example is what tells the options apart to somebody who
 * has only ever read one of them.
 */
@Composable
private fun NotationChoice(
    selected: UserPreferences.Notation?,
    isEnabled: Boolean,
    onSelected: (UserPreferences.Notation) -> Unit,
) = SettingsSubsection(
    title = stringResource(Res.string.settings_notation),
    description = stringResource(Res.string.settings_notation_description),
    isEnabled = isEnabled,
) {
    Column(modifier = Modifier.selectableGroup()) {
        listOf(
            Triple(UserPreferences.Notation.STANDARD, Res.string.settings_notation_standard, Res.string.settings_notation_standard_description),
            Triple(UserPreferences.Notation.GERMAN, Res.string.settings_notation_german, Res.string.settings_notation_german_description),
            Triple(UserPreferences.Notation.LATIN, Res.string.settings_notation_latin, Res.string.settings_notation_latin_description),
            Triple(UserPreferences.Notation.NASHVILLE, Res.string.settings_notation_nashville, Res.string.settings_notation_nashville_description),
            Triple(UserPreferences.Notation.ROMAN, Res.string.settings_notation_roman, Res.string.settings_notation_roman_description),
        ).forEach { (notation, title, description) ->
            RadioListItem(
                title = stringResource(title),
                description = stringResource(description),
                isSelected = selected == notation,
                isEnabled = isEnabled,
                onSelected = { onSelected(notation) },
            )
        }
    }
}
