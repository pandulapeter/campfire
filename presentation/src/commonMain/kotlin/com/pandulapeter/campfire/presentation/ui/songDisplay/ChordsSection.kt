/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songDisplay

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.settings_accidentals
import com.pandulapeter.campfire.presentation.resources.settings_accidentals_description
import com.pandulapeter.campfire.presentation.resources.settings_accidentals_flats
import com.pandulapeter.campfire.presentation.resources.settings_accidentals_original
import com.pandulapeter.campfire.presentation.resources.settings_accidentals_sharps
import com.pandulapeter.campfire.presentation.resources.settings_group_chords
import com.pandulapeter.campfire.presentation.resources.settings_notation
import com.pandulapeter.campfire.presentation.resources.settings_notation_choose
import com.pandulapeter.campfire.presentation.resources.settings_notation_description
import com.pandulapeter.campfire.presentation.ui.chords.label
import com.pandulapeter.campfire.presentation.ui.chords.shownChordName
import com.pandulapeter.campfire.presentation.mapper.toChordNotation
import com.pandulapeter.campfire.presentation.ui.components.list.LIST_ITEM_KEYLINE
import com.pandulapeter.campfire.presentation.ui.components.controls.PickerChip
import com.pandulapeter.campfire.presentation.ui.components.controls.SegmentedChoice
import com.pandulapeter.campfire.presentation.ui.settingsPage.SettingsSubsection
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.settingsPage.SettingsSection

/**
 * How the chords are written, first in the Preferences tab and in the song display sheet. The instrument is not here: it
 * is picked where it is used (song details, the Tuner, the Chords and the Scales tabs, and the onboarding).
 */
@Composable
internal fun ChordsSection(
    userPreferences: UserPreferences?,
    onNotationClicked: () -> Unit,
    onAccidentalsSelected: (UserPreferences.Accidentals) -> Unit,
) = SettingsSection(title = stringResource(Res.string.settings_group_chords)) {
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
        onClicked = onNotationClicked,
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
            onSelected = onAccidentalsSelected,
        )
    }
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
    get() = (this?.areSongsEnabled != false && this?.areChordsEnabled != false) || this?.isChordLibraryEnabled != false ||
        this?.areScalesEnabled != false
