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
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.metronome
import com.pandulapeter.campfire.presentation.resources.scales
import com.pandulapeter.campfire.presentation.resources.setlists
import com.pandulapeter.campfire.presentation.resources.settings_chords
import com.pandulapeter.campfire.presentation.resources.settings_chord_diagrams
import com.pandulapeter.campfire.presentation.resources.settings_chord_diagrams_description
import com.pandulapeter.campfire.presentation.resources.settings_chord_degrees
import com.pandulapeter.campfire.presentation.resources.settings_chord_degrees_description
import com.pandulapeter.campfire.presentation.resources.settings_chords_description
import com.pandulapeter.campfire.presentation.resources.settings_cover_art
import com.pandulapeter.campfire.presentation.resources.settings_cover_art_description
import com.pandulapeter.campfire.presentation.resources.settings_everything_off
import com.pandulapeter.campfire.presentation.resources.settings_group_online
import com.pandulapeter.campfire.presentation.resources.settings_group_playing
import com.pandulapeter.campfire.presentation.resources.settings_metronome_description
import com.pandulapeter.campfire.presentation.resources.settings_scale_suggestions
import com.pandulapeter.campfire.presentation.resources.settings_scale_suggestions_description
import com.pandulapeter.campfire.presentation.resources.settings_scales_description
import com.pandulapeter.campfire.presentation.resources.settings_setlists_description
import com.pandulapeter.campfire.presentation.resources.settings_song_details_lookup
import com.pandulapeter.campfire.presentation.resources.settings_song_details_lookup_description
import com.pandulapeter.campfire.presentation.resources.settings_songs_description
import com.pandulapeter.campfire.presentation.resources.settings_songs_description_without_setlists
import com.pandulapeter.campfire.presentation.resources.songs
import com.pandulapeter.campfire.presentation.resources.settings_tuner_description
import com.pandulapeter.campfire.presentation.resources.tuner
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.SwitchListItem
import com.pandulapeter.campfire.presentation.ui.components.WarningCard
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.navigation.FeatureSwitches
import com.pandulapeter.campfire.presentation.ui.settingsPage.AnimatedSettingsRow
import com.pandulapeter.campfire.presentation.ui.settingsPage.SettingsSection

/**
 * The parts of the app a reader can do without, each a switch that takes it out of every screen it is on, so that a
 * singer is not handed a metronome and a band that plays from one list is not handed a setlist tab. They follow the
 * tabs of the navigation in their order - songs, setlists, metronome, tuner, chords, scales - each switch that has no tab of its own
 * right after the one it depends on: the chords before the chord diagrams that are their tab and the chord degrees that label those, the scale suggestions
 * after the scales. The two switches that let the app reach the network are a group of their own, so that what can
 * make a request is found in one place rather than among the rest.
 */
@Composable
internal fun FeaturesSections(
    viewModel: CampfireViewModel,
    userPreferences: UserPreferences?,
) {
    PlayingSection(viewModel = viewModel, userPreferences = userPreferences)
    OnlineSection(viewModel = viewModel, userPreferences = userPreferences)
}

@Composable
private fun PlayingSection(
    viewModel: CampfireViewModel,
    userPreferences: UserPreferences?,
) = SettingsSection(title = stringResource(Res.string.settings_group_playing)) {
    // With every screen switched off Settings is the only one left, and the app opens on it (TopLevel.home).
    AnimatedSettingsRow(isVisible = CampfireDestination.TopLevel.home(FeatureSwitches.of(userPreferences)) == CampfireDestination.Settings) {
        EverythingOffCard()
    }
    SwitchListItem(
        title = stringResource(Res.string.songs),
        // The setlists go with the songs, so they are named only while their own switch has not taken them already.
        description = stringResource(
            if (userPreferences?.areSetlistsEnabled != false) Res.string.settings_songs_description else Res.string.settings_songs_description_without_setlists,
        ),
        isChecked = userPreferences?.areSongsEnabled != false,
        onCheckedChange = viewModel.preferencesController::setSongsEnabled,
    )
    // Disabled rather than hidden with the songs off, as the chord diagrams are with the chords off: a setlist is a list
    // of songs, so without them there is nothing it could hold.
    SwitchListItem(
        title = stringResource(Res.string.setlists),
        description = stringResource(Res.string.settings_setlists_description),
        isChecked = userPreferences?.areSetlistsEnabled != false,
        isEnabled = userPreferences?.areSongsEnabled != false,
        onCheckedChange = viewModel.preferencesController::setSetlistsEnabled,
    )
    SwitchListItem(
        title = stringResource(Res.string.metronome),
        description = stringResource(Res.string.settings_metronome_description),
        isChecked = userPreferences?.isMetronomeEnabled != false,
        onCheckedChange = viewModel.preferencesController::setMetronomeEnabled,
    )
    // Off, nothing in the app can ask for the microphone.
    SwitchListItem(
        title = stringResource(Res.string.tuner),
        description = stringResource(Res.string.settings_tuner_description),
        isChecked = userPreferences?.isTunerEnabled != false,
        onCheckedChange = viewModel.preferencesController::setTunerEnabled,
    )
    SwitchListItem(
        title = stringResource(Res.string.settings_chords),
        description = stringResource(Res.string.settings_chords_description),
        isChecked = userPreferences?.areChordsEnabled != false,
        onCheckedChange = viewModel.preferencesController::setChordsEnabled,
    )
    // Disabled rather than hidden with the chords off, like the chord spelling: there is nothing to finger then, and
    // what it is set to is still what the songs will open with once the chords are back.
    SwitchListItem(
        title = stringResource(Res.string.settings_chord_diagrams),
        description = stringResource(Res.string.settings_chord_diagrams_description),
        isChecked = userPreferences?.areChordDiagramsEnabled != false,
        isEnabled = userPreferences?.areChordsEnabled != false,
        onCheckedChange = viewModel.preferencesController::setChordDiagramsEnabled,
    )
    // Disabled rather than hidden with the chords or their diagrams off, as the scale suggestions are with the scales off:
    // a degree labels a diagram, so without them there is nothing it could name.
    SwitchListItem(
        title = stringResource(Res.string.settings_chord_degrees),
        description = stringResource(Res.string.settings_chord_degrees_description),
        isChecked = userPreferences?.areChordDegreesEnabled != false,
        isEnabled = userPreferences?.areChordsEnabled != false && userPreferences?.areChordDiagramsEnabled != false,
        onCheckedChange = viewModel.preferencesController::setChordDegreesEnabled,
    )
    // Not tied to either chord switch: a scale is played on any instrument, a violin with no chords included.
    SwitchListItem(
        title = stringResource(Res.string.scales),
        description = stringResource(Res.string.settings_scales_description),
        isChecked = userPreferences?.areScalesEnabled != false,
        onCheckedChange = viewModel.preferencesController::setScalesEnabled,
    )
    // Disabled rather than hidden with the scales or the chords off, as the chord diagrams are with the chords off: a
    // suggestion is read off a song's chords, so without them there is nothing it could show.
    SwitchListItem(
        title = stringResource(Res.string.settings_scale_suggestions),
        description = stringResource(Res.string.settings_scale_suggestions_description),
        isChecked = userPreferences?.areScaleSuggestionsEnabled != false,
        isEnabled = userPreferences?.areScalesEnabled != false && userPreferences?.areChordsEnabled != false,
        onCheckedChange = viewModel.preferencesController::setScaleSuggestionsEnabled,
    )
}

/** What the app has become with every screen switched off, announced since it arrives with the switch just flipped. */
@Composable
private fun EverythingOffCard() = WarningCard(
    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    text = stringResource(Res.string.settings_everything_off),
    isAnnounced = true,
)

@Composable
private fun OnlineSection(
    viewModel: CampfireViewModel,
    userPreferences: UserPreferences?,
) = SettingsSection(title = stringResource(Res.string.settings_group_online)) {
    // Both are about songs, so both are disabled while the songs are off, the way the setlists are.
    val areSongsEnabled = userPreferences?.areSongsEnabled != false
    // Off, nothing is asked of either catalogue for a song's details: the button in the Edit song details form is gone.
    SwitchListItem(
        title = stringResource(Res.string.settings_song_details_lookup),
        description = stringResource(Res.string.settings_song_details_lookup_description),
        isChecked = userPreferences?.isSongDetailsLookupEnabled == true,
        isEnabled = areSongsEnabled,
        onCheckedChange = viewModel.preferencesController::setSongDetailsLookupEnabled,
    )
    // Off, no cover is fetched from anywhere, which is what somebody who does not want a song file to make the app
    // contact a host turns it off for.
    SwitchListItem(
        title = stringResource(Res.string.settings_cover_art),
        description = stringResource(Res.string.settings_cover_art_description),
        isChecked = userPreferences?.isCoverArtEnabled == true,
        isEnabled = areSongsEnabled,
        onCheckedChange = viewModel.preferencesController::setCoverArtEnabled,
    )
}
