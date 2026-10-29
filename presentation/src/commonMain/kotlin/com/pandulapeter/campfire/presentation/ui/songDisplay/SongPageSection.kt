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

import androidx.compose.runtime.Composable
import com.pandulapeter.campfire.data.model.preferences.SongLayout
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.settings_chord_degrees
import com.pandulapeter.campfire.presentation.resources.settings_chord_degrees_description
import com.pandulapeter.campfire.presentation.resources.settings_chord_diagrams
import com.pandulapeter.campfire.presentation.resources.settings_chord_diagrams_description
import com.pandulapeter.campfire.presentation.resources.settings_chords
import com.pandulapeter.campfire.presentation.resources.settings_chords_description
import com.pandulapeter.campfire.presentation.resources.settings_group_song_page
import com.pandulapeter.campfire.presentation.resources.settings_number_sections
import com.pandulapeter.campfire.presentation.resources.settings_number_sections_description
import com.pandulapeter.campfire.presentation.resources.settings_scale_suggestions
import com.pandulapeter.campfire.presentation.resources.settings_scale_suggestions_description
import com.pandulapeter.campfire.presentation.resources.settings_song_layout
import com.pandulapeter.campfire.presentation.resources.settings_song_layout_description
import com.pandulapeter.campfire.presentation.resources.song_layout_continuous
import com.pandulapeter.campfire.presentation.resources.song_layout_pages
import com.pandulapeter.campfire.presentation.ui.components.SwitchListItem
import com.pandulapeter.campfire.presentation.ui.components.controls.SegmentedChoice
import com.pandulapeter.campfire.presentation.ui.settingsPage.SettingsSection
import com.pandulapeter.campfire.presentation.ui.settingsPage.SettingsSubsection
import com.pandulapeter.campfire.presentation.ui.state.PreferencesController

/**
 * How a song's page is read, in the Preferences tab and in the song display sheet: first its layout, pages or one column
 * that scrolls by itself, the one choice here that changes how the page is used rather than what is on it, and so a
 * segmented control rather than one more switch; then whether a reader sees the chords at all, and what the app adds to
 * them and to the song's sections. Each only hides, and each is
 * disabled rather than hidden while another takes what it adds, since what it is set to is still what the songs will
 * open with once that is back.
 */
@Composable
internal fun SongPageSection(
    userPreferences: UserPreferences?,
    preferencesController: PreferencesController,
) = SettingsSection(title = stringResource(Res.string.settings_group_song_page)) {
    SettingsSubsection(
        title = stringResource(Res.string.settings_song_layout),
        description = stringResource(Res.string.settings_song_layout_description),
    ) {
        SegmentedChoice(
            options = listOf(
                SongLayout.PAGES to stringResource(Res.string.song_layout_pages),
                SongLayout.CONTINUOUS to stringResource(Res.string.song_layout_continuous),
            ),
            selected = userPreferences?.songLayout,
            onSelected = preferencesController::setSongLayout,
        )
    }
    val areChordsEnabled = userPreferences?.areChordsEnabled != false
    val areChordDiagramsEnabled = userPreferences?.areChordDiagramsEnabled != false
    SwitchListItem(
        title = stringResource(Res.string.settings_chords),
        description = stringResource(Res.string.settings_chords_description),
        isChecked = areChordsEnabled,
        onCheckedChange = preferencesController::setChordsEnabled,
    )
    SwitchListItem(
        title = stringResource(Res.string.settings_chord_diagrams),
        description = stringResource(Res.string.settings_chord_diagrams_description),
        isChecked = areChordDiagramsEnabled,
        isEnabled = areChordsEnabled,
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
        isEnabled = (areChordsEnabled && areChordDiagramsEnabled && !isNumbering) ||
            (userPreferences.isChordLibraryEnabled && userPreferences.areScalesEnabled),
        onCheckedChange = preferencesController::setChordDegreesEnabled,
    )
    // A suggestion is read off a song's chords and opens a scale's sheet, so it needs both the chords and the scales.
    SwitchListItem(
        title = stringResource(Res.string.settings_scale_suggestions),
        description = stringResource(Res.string.settings_scale_suggestions_description),
        isChecked = userPreferences?.areScaleSuggestionsEnabled != false,
        isEnabled = userPreferences?.areScalesEnabled != false && areChordsEnabled,
        onCheckedChange = preferencesController::setScaleSuggestionsEnabled,
    )
    SwitchListItem(
        title = stringResource(Res.string.settings_number_sections),
        description = stringResource(Res.string.settings_number_sections_description),
        isChecked = userPreferences?.shouldNumberSections == true,
        onCheckedChange = preferencesController::setSectionNumberingEnabled,
    )
}
