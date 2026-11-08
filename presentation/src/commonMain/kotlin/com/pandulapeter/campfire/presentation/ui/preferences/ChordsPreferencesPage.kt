/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.preferences

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pandulapeter.campfire.data.model.preferences.ChordNames
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.settings_accidentals
import com.pandulapeter.campfire.presentation.resources.settings_accidentals_description
import com.pandulapeter.campfire.presentation.resources.settings_accidentals_flats
import com.pandulapeter.campfire.presentation.resources.settings_accidentals_original
import com.pandulapeter.campfire.presentation.resources.settings_accidentals_sharps
import com.pandulapeter.campfire.presentation.resources.settings_capo_keeps_key
import com.pandulapeter.campfire.presentation.resources.settings_capo_keeps_key_description
import com.pandulapeter.campfire.presentation.resources.settings_chord_names
import com.pandulapeter.campfire.presentation.resources.settings_chord_names_description
import com.pandulapeter.campfire.presentation.resources.settings_chord_names_shapes
import com.pandulapeter.campfire.presentation.resources.settings_chord_names_sounding
import com.pandulapeter.campfire.presentation.resources.settings_chord_degrees
import com.pandulapeter.campfire.presentation.resources.settings_chord_degrees_description
import com.pandulapeter.campfire.presentation.resources.settings_chord_diagrams
import com.pandulapeter.campfire.presentation.resources.settings_chord_diagrams_description
import com.pandulapeter.campfire.presentation.resources.settings_group_chord_names
import com.pandulapeter.campfire.presentation.resources.settings_group_diagrams
import com.pandulapeter.campfire.presentation.resources.settings_instrument
import com.pandulapeter.campfire.presentation.resources.settings_instrument_description
import com.pandulapeter.campfire.presentation.resources.settings_notation
import com.pandulapeter.campfire.presentation.resources.settings_notation_choose
import com.pandulapeter.campfire.presentation.resources.settings_notation_description
import com.pandulapeter.campfire.presentation.ui.chords.label
import com.pandulapeter.campfire.presentation.ui.chords.shownChordName
import com.pandulapeter.campfire.presentation.mapper.toChordNotation
import com.pandulapeter.campfire.presentation.ui.components.SwitchListItem
import com.pandulapeter.campfire.presentation.ui.components.list.LIST_ITEM_KEYLINE
import com.pandulapeter.campfire.presentation.ui.components.controls.PickerChip
import com.pandulapeter.campfire.presentation.ui.components.controls.SegmentedChoice
import com.pandulapeter.campfire.presentation.ui.settingsPage.SettingsSubsection
import com.pandulapeter.campfire.presentation.ui.state.PreferencesController
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.instrument.InstrumentPill
import com.pandulapeter.campfire.presentation.ui.instrument.instrumentTuning
import com.pandulapeter.campfire.presentation.ui.settingsPage.SettingsSection

/**
 * The Preferences' Chords page, the same in Settings and in the sheet the song details, the Chords, and the Scales tabs
 * open: first how the chords are drawn (the instrument in the player's hands, then whether a song shows its diagrams and
 * their degrees), then how they are written (their notation, their accidentals, whether a song's chords name the shapes
 * fingered above its capo or what sounds, and whether its capo stepper keeps the key). The instrument is also picked
 * where it is used (song details, the Tuner, the Chords and the Scales tabs, and the onboarding): this is the one place
 * that finds it with the rest of how a chord is shown.
 */
@Composable
internal fun ChordsPreferencesPage(
    userPreferences: UserPreferences?,
    preferencesController: PreferencesController,
    showDialog: (DialogType) -> Unit,
) {
    ChordDiagramsSection(userPreferences = userPreferences, preferencesController = preferencesController, showDialog = showDialog)
    ChordNamesSection(userPreferences = userPreferences, preferencesController = preferencesController, showDialog = showDialog)
}

/** The instrument the diagrams are drawn for, whether a song shows them, and whether they carry their degrees. */
@Composable
private fun ChordDiagramsSection(
    userPreferences: UserPreferences?,
    preferencesController: PreferencesController,
    showDialog: (DialogType) -> Unit,
) = SettingsSection(title = stringResource(Res.string.settings_group_diagrams)) {
    val notation = (userPreferences?.chordSpelling ?: UserPreferences.ChordSpelling.Default).notation.toChordNotation()
    SettingsSubsection(
        title = stringResource(Res.string.settings_instrument),
        description = stringResource(Res.string.settings_instrument_description),
    ) {
        InstrumentPill(
            modifier = Modifier.padding(horizontal = LIST_ITEM_KEYLINE),
            instrument = userPreferences?.instrument ?: UserPreferences.Instrument.GUITAR,
            tuning = userPreferences?.instrumentTuning,
            notation = notation,
            // Over this page's sheet rather than in its place, so that picking one comes back here (DialogStack).
            onInstrumentClicked = { showDialog(DialogType.Instrument) },
            onTuningClicked = { showDialog(DialogType.Tuning) },
        )
    }
    val areChordsEnabled = userPreferences?.areChordsEnabled != false
    val areChordDiagramsEnabled = userPreferences?.areChordDiagramsEnabled != false
    val areSongsEnabled = userPreferences?.areSongsEnabled != false
    SwitchListItem(
        title = stringResource(Res.string.settings_chord_diagrams),
        description = stringResource(Res.string.settings_chord_diagrams_description),
        isChecked = areChordDiagramsEnabled,
        isEnabled = areSongsEnabled && areChordsEnabled,
        onCheckedChange = preferencesController::setChordDiagramsEnabled,
    )
    // A degree labels a diagram, a song's or one of the chords a scale sheet lists (which needs both the Scales and the
    // Chords tab), so it is disabled only where neither is drawn. A numbering already names a song's chords by their
    // degrees, so its diagrams carry none, while a scale sheet's chords keep their letters and their numerals.
    val isNumbering = userPreferences?.chordSpelling?.notation?.isNumbering == true
    SwitchListItem(
        title = stringResource(Res.string.settings_chord_degrees),
        description = stringResource(Res.string.settings_chord_degrees_description),
        isChecked = userPreferences?.areChordDegreesEnabled == true,
        isEnabled = (areSongsEnabled && areChordsEnabled && areChordDiagramsEnabled && !isNumbering) ||
            (userPreferences.isChordLibraryEnabled && userPreferences.areScalesEnabled),
        onCheckedChange = preferencesController::setChordDegreesEnabled,
    )
}

/**
 * How the chords are written: their notation, their accidentals, whether a song's chords name the shapes fingered above
 * its capo or what sounds, and whether its capo stepper keeps the key.
 */
@Composable
private fun ChordNamesSection(
    userPreferences: UserPreferences?,
    preferencesController: PreferencesController,
    showDialog: (DialogType) -> Unit,
) = SettingsSection(title = stringResource(Res.string.settings_group_chord_names)) {
    // The notation and the accidentals only decide how a chord or a key is written, so with nothing on that names one
    // they have nothing to say. They stay in the section rather than disappearing from it: what they are set to is still
    // what the chords will look like as soon as they are shown again. The accidentals stay enabled under a numbering,
    // since they still spell the key it counts from.
    val isEnabled = userPreferences.namesChords
    // The examples under the settings below are written in the notation picked here, so they change with it.
    val notation = (userPreferences?.chordSpelling ?: UserPreferences.ChordSpelling.Default).notation.toChordNotation()
    NotationChoice(
        selected = (userPreferences?.chordSpelling ?: UserPreferences.ChordSpelling.Default).notation,
        isEnabled = isEnabled,
        // Over this page's sheet rather than in its place, so that picking one comes back here (DialogStack).
        onClicked = { showDialog(DialogType.Notation) },
    )
    SettingsSubsection(
        title = stringResource(Res.string.settings_accidentals),
        description = stringResource(
            Res.string.settings_accidentals_description,
            shownChordName("Bb", notation),
            shownChordName("A#", notation),
        ),
        isEnabled = isEnabled,
    ) {
        SegmentedChoice(
            options = listOf(
                UserPreferences.Accidentals.ORIGINAL to stringResource(Res.string.settings_accidentals_original),
                UserPreferences.Accidentals.FLATS to stringResource(Res.string.settings_accidentals_flats),
                UserPreferences.Accidentals.SHARPS to stringResource(Res.string.settings_accidentals_sharps),
            ),
            selected = userPreferences?.chordSpelling?.accidentals,
            isEnabled = isEnabled,
            onSelected = preferencesController::setAccidentals,
        )
    }
    // Only a song's chords are named by their shapes, so it says nothing with the songs' chords off, and nothing on the
    // keyboard, which is fingered as it sounds and always reads what sounds, as its description says.
    val canNameShapes = userPreferences?.areSongsEnabled != false && userPreferences?.areChordsEnabled != false &&
        userPreferences?.instrument != UserPreferences.Instrument.KEYBOARD
    SettingsSubsection(
        title = stringResource(Res.string.settings_chord_names),
        description = stringResource(Res.string.settings_chord_names_description),
        isEnabled = canNameShapes,
    ) {
        SegmentedChoice(
            options = listOf(
                ChordNames.SHAPES to stringResource(Res.string.settings_chord_names_shapes),
                ChordNames.SOUNDING to stringResource(Res.string.settings_chord_names_sounding),
            ),
            selected = userPreferences?.chordNames,
            isEnabled = canNameShapes,
            onSelected = preferencesController::setChordNames,
        )
    }
    // Unlike the names, this moves what the band hears, so it applies on the keyboard too, whose capo stepper is the
    // band's.
    SwitchListItem(
        title = stringResource(Res.string.settings_capo_keeps_key),
        description = stringResource(Res.string.settings_capo_keeps_key_description),
        isChecked = userPreferences?.capoKeepsKey == true,
        isEnabled = userPreferences?.areSongsEnabled != false && userPreferences?.areChordsEnabled != false,
        onCheckedChange = preferencesController::setCapoKeepsKey,
    )
}

/**
 * The notation chords are written in, as a chip naming it that opens a sheet of the five ([DialogType.Notation]), like
 * the instrument's chips elsewhere: a list of five radio rows was the longest thing on the tab for a choice made once.
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

/** Whether anything shown names a chord or a key: a song's chords, the Chords tab, or the Scales tab. */
private val UserPreferences?.namesChords
    get() = (this?.areSongsEnabled != false && this?.areChordsEnabled != false) || this.isChordLibraryEnabled ||
        this.areScalesEnabled
