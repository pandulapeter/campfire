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

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pandulapeter.campfire.chordpro.chords.ChordProNotation
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.instrument_description
import com.pandulapeter.campfire.presentation.resources.settings_accidentals
import com.pandulapeter.campfire.presentation.resources.settings_accidentals_description
import com.pandulapeter.campfire.presentation.resources.settings_accidentals_flats
import com.pandulapeter.campfire.presentation.resources.settings_accidentals_original
import com.pandulapeter.campfire.presentation.resources.settings_accidentals_sharps
import com.pandulapeter.campfire.presentation.resources.settings_group_chords
import com.pandulapeter.campfire.presentation.resources.settings_notation
import com.pandulapeter.campfire.presentation.resources.settings_notation_choose
import com.pandulapeter.campfire.presentation.resources.settings_notation_description
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.chords.label
import com.pandulapeter.campfire.presentation.mapper.toChordNotation
import com.pandulapeter.campfire.presentation.ui.components.InstrumentSetting
import com.pandulapeter.campfire.presentation.ui.components.list.LIST_ITEM_KEYLINE
import com.pandulapeter.campfire.presentation.ui.components.PickerChip
import com.pandulapeter.campfire.presentation.ui.components.SegmentedChoice
import com.pandulapeter.campfire.presentation.ui.settingsPage.SettingsSubsection
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.instrument.instrumentTuning
import com.pandulapeter.campfire.presentation.ui.settingsPage.SettingsSection

/**
 * The first section of the Songs tab: what the chords are played on and how they are written. The instrument comes
 * first, since it decides the shapes, the tuner, and how the capo is read, and since it is the one row here that the
 * chords switched off can leave enabled; the notation and the accidentals, which only decide how a chord is written, come after it.
 */
@Composable
internal fun ChordsSection(
    viewModel: CampfireViewModel,
    userPreferences: UserPreferences?,
) = SettingsSection(title = stringResource(Res.string.settings_group_chords)) {
    // The notation and the accidentals only decide how a chord is written, so with the chords switched off they have
    // nothing to say. They stay in the section rather than disappearing from it: what they are set to is still what the
    // chords will look like as soon as they are shown again. The accidentals stay enabled under a numbering, since they
    // still spell the key it counts from.
    val areChordsEnabled = userPreferences?.areChordsEnabled != false
    // The examples under the settings below are written in the notation picked here, so they change with it.
    val notation = (userPreferences?.chordSpelling ?: UserPreferences.ChordSpelling.Default).notation.toChordNotation()
    // Enabled with the diagrams switched off too, since the keyboard also reads the capo as a transposition, and with the
    // chords switched off while the tuner is on, which listens for the same instrument.
    val isInstrumentEnabled = areChordsEnabled || userPreferences.isTunerEnabled
    userPreferences?.let { preferences ->
        InstrumentSetting(
            instrument = preferences.instrument,
            tuning = preferences.instrumentTuning,
            notation = notation,
            description = stringResource(Res.string.instrument_description),
            isEnabled = isInstrumentEnabled,
            onInstrumentClicked = { viewModel.showDialog(DialogType.Instrument) },
            onTuningClicked = { viewModel.showDialog(DialogType.Tuning) },
        )
    }
    NotationChoice(
        selected = (userPreferences?.chordSpelling ?: UserPreferences.ChordSpelling.Default).notation,
        isEnabled = areChordsEnabled,
        onClicked = { viewModel.showDialog(DialogType.Notation) },
    )
    SettingsSubsection(
        title = stringResource(Res.string.settings_accidentals),
        description = stringResource(
            Res.string.settings_accidentals_description,
            ChordProNotation.shownName("Bb", notation),
            ChordProNotation.shownName("A#", notation),
        ),
        isEnabled = areChordsEnabled,
    ) {
        SegmentedChoice(
            options = listOf(
                UserPreferences.Accidentals.ORIGINAL to stringResource(Res.string.settings_accidentals_original),
                UserPreferences.Accidentals.FLATS to stringResource(Res.string.settings_accidentals_flats),
                UserPreferences.Accidentals.SHARPS to stringResource(Res.string.settings_accidentals_sharps),
            ),
            selected = userPreferences?.chordSpelling?.accidentals,
            isEnabled = areChordsEnabled,
            onSelected = viewModel::setAccidentals,
        )
    }
}

/**
 * The notation chords are written in, as a chip naming it that opens a sheet of the five ([DialogType.Notation]), like
 * the instrument's chips above it: a list of five radio rows was the longest thing on the tab for a choice made once.
 */
@Composable
private fun NotationChoice(
    selected: UserPreferences.Notation,
    isEnabled: Boolean,
    onClicked: () -> Unit,
) = SettingsSubsection(
    title = stringResource(Res.string.settings_notation),
    description = stringResource(Res.string.settings_notation_description),
    isEnabled = isEnabled,
) {
    PickerChip(
        modifier = Modifier.padding(horizontal = LIST_ITEM_KEYLINE),
        text = stringResource(selected.label),
        onClickLabel = stringResource(Res.string.settings_notation_choose),
        isEnabled = isEnabled,
        onClick = onClicked,
    )
}
