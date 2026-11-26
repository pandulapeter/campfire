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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.Composable
import com.pandulapeter.campfire.data.model.preferences.PageTurn
import com.pandulapeter.campfire.data.model.preferences.SongLayout
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.metronome
import com.pandulapeter.campfire.presentation.resources.settings_chord_diagrams
import com.pandulapeter.campfire.presentation.resources.settings_chord_diagrams_description
import com.pandulapeter.campfire.presentation.resources.settings_show_chords
import com.pandulapeter.campfire.presentation.resources.settings_dock_metronome_description
import com.pandulapeter.campfire.presentation.resources.settings_dock_tuner_description
import com.pandulapeter.campfire.presentation.resources.settings_group_dock
import com.pandulapeter.campfire.presentation.resources.settings_chords_description
import com.pandulapeter.campfire.presentation.resources.page_turn_column
import com.pandulapeter.campfire.presentation.resources.page_turn_page
import com.pandulapeter.campfire.presentation.resources.settings_group_song_page
import com.pandulapeter.campfire.presentation.resources.settings_number_sections
import com.pandulapeter.campfire.presentation.resources.settings_number_sections_description
import com.pandulapeter.campfire.presentation.resources.settings_page_turn
import com.pandulapeter.campfire.presentation.resources.settings_page_turn_description
import com.pandulapeter.campfire.presentation.resources.settings_scale_suggestions
import com.pandulapeter.campfire.presentation.resources.settings_scale_suggestions_description
import com.pandulapeter.campfire.presentation.resources.settings_song_layout
import com.pandulapeter.campfire.presentation.resources.settings_song_layout_description
import com.pandulapeter.campfire.presentation.resources.song_layout_continuous
import com.pandulapeter.campfire.presentation.resources.song_layout_pages
import com.pandulapeter.campfire.presentation.resources.tuner
import com.pandulapeter.campfire.presentation.ui.components.SwitchListItem
import com.pandulapeter.campfire.presentation.ui.components.controls.SegmentedChoice
import com.pandulapeter.campfire.presentation.ui.settingsPage.AnimatedSettingsRow
import com.pandulapeter.campfire.presentation.ui.settingsPage.SettingsSection
import com.pandulapeter.campfire.presentation.ui.settingsPage.SettingsSubsection
import com.pandulapeter.campfire.presentation.ui.state.PreferencesController

/**
 * The Preferences' Song page, the same in Settings and in the song details screen's sheet, which opens on it: first a
 * song's layout, pages or one column that scrolls by itself, the one choice here that changes how the page is used rather
 * than what is on it, and so a segmented control rather than one more switch, with how pages are turned under it while
 * they are picked; then whether a reader sees the chords at
 * all (how they are drawn and written is the Chords page's) and how the song's sections are named; then what the dock
 * beside the song holds ([DockSection]).
 * Each only hides, and each is disabled rather than hidden while another preference takes what it adds, since what it
 * is set to is still what the songs will open with once that is back; a row whose Feature is off is hidden instead.
 */
@Composable
internal fun SongPreferencesPage(
    userPreferences: UserPreferences?,
    preferencesController: PreferencesController,
) {
    SongPageSection(userPreferences = userPreferences, preferencesController = preferencesController)
    DockSection(userPreferences = userPreferences, preferencesController = preferencesController)
}

/** How a song is laid out and turned, whether its chords are shown, and how its sections are named. */
@Composable
private fun SongPageSection(
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
    // Only pages are turned, so the choice of how is there only while they are picked.
    AnimatedVisibility(
        visible = userPreferences?.songLayout != SongLayout.CONTINUOUS,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        SettingsSubsection(
            title = stringResource(Res.string.settings_page_turn),
            description = stringResource(Res.string.settings_page_turn_description),
        ) {
            SegmentedChoice(
                options = listOf(
                    PageTurn.PAGE to stringResource(Res.string.page_turn_page),
                    PageTurn.COLUMN to stringResource(Res.string.page_turn_column),
                ),
                selected = userPreferences?.pageTurn,
                onSelected = preferencesController::setPageTurn,
            )
        }
    }
    val areChordsEnabled = userPreferences?.areChordsEnabled != false
    SwitchListItem(
        title = stringResource(Res.string.settings_show_chords),
        description = stringResource(Res.string.settings_chords_description),
        isChecked = areChordsEnabled,
        onCheckedChange = preferencesController::setChordsEnabled,
    )
    SwitchListItem(
        title = stringResource(Res.string.settings_number_sections),
        description = stringResource(Res.string.settings_number_sections_description),
        isChecked = userPreferences?.shouldNumberSections == true,
        onCheckedChange = preferencesController::setSectionNumberingEnabled,
    )
}

/**
 * What song details' dock holds, every switch that adds to it in one place: the diagrams of a song's chords and its scale
 * suggestions (each also in the editor's preview and the PDF), its click, and the tuner in its instrument card. A switch
 * whose Feature is off (the Scales, the Metronome, the Tuner) is hidden with it, fading and folding away
 * (`FeatureRows.kt`); one waiting for another preference (the chords, the tuner's card included,
 * [isDockTunerSwitchEnabled]) is disabled. The diagrams' switch is always there, so the section never empties. The app
 * bar's key chip has no switch: it is the only way to the transposition and the capo.
 */
@Composable
private fun DockSection(
    userPreferences: UserPreferences?,
    preferencesController: PreferencesController,
) = SettingsSection(title = stringResource(Res.string.settings_group_dock)) {
    val areChordsEnabled = userPreferences?.areChordsEnabled != false
    SwitchListItem(
        title = stringResource(Res.string.settings_chord_diagrams),
        description = stringResource(Res.string.settings_chord_diagrams_description),
        isChecked = userPreferences?.areChordDiagramsEnabled != false,
        isEnabled = areChordsEnabled,
        onCheckedChange = preferencesController::setChordDiagramsEnabled,
    )
    // A suggestion is read off a song's chords and opens a scale's sheet, so it needs both the chords and the scales.
    AnimatedSettingsRow(isVisible = isScaleSuggestionsSwitchShown(userPreferences)) {
        SwitchListItem(
            title = stringResource(Res.string.settings_scale_suggestions),
            description = stringResource(Res.string.settings_scale_suggestions_description),
            isChecked = userPreferences?.areScaleSuggestionsEnabled != false,
            isEnabled = areChordsEnabled,
            onCheckedChange = preferencesController::setScaleSuggestionsEnabled,
        )
    }
    AnimatedSettingsRow(isVisible = isDockMetronomeSwitchShown(userPreferences)) {
        SwitchListItem(
            title = stringResource(Res.string.metronome),
            description = stringResource(Res.string.settings_dock_metronome_description),
            isChecked = userPreferences?.isDockMetronomeEnabled != false,
            onCheckedChange = preferencesController::setDockMetronomeEnabled,
        )
    }
    AnimatedSettingsRow(isVisible = isDockTunerSwitchShown(userPreferences)) {
        SwitchListItem(
            title = stringResource(Res.string.tuner),
            description = stringResource(Res.string.settings_dock_tuner_description),
            isChecked = userPreferences?.isDockTunerEnabled != false,
            isEnabled = isDockTunerSwitchEnabled(userPreferences),
            onCheckedChange = preferencesController::setDockTunerEnabled,
        )
    }
}
