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
import com.pandulapeter.campfire.presentation.resources.chords
import com.pandulapeter.campfire.presentation.resources.metronome
import com.pandulapeter.campfire.presentation.resources.scales
import com.pandulapeter.campfire.presentation.resources.setlists
import com.pandulapeter.campfire.presentation.resources.settings_chord_library_description
import com.pandulapeter.campfire.presentation.resources.settings_cover_art
import com.pandulapeter.campfire.presentation.resources.settings_cover_art_description
import com.pandulapeter.campfire.presentation.resources.settings_everything_off
import com.pandulapeter.campfire.presentation.resources.settings_group_online
import com.pandulapeter.campfire.presentation.resources.settings_group_playing
import com.pandulapeter.campfire.presentation.resources.settings_metronome_description
import com.pandulapeter.campfire.presentation.resources.settings_random_picks_description
import com.pandulapeter.campfire.presentation.resources.settings_scales_description
import com.pandulapeter.campfire.presentation.resources.settings_setlists_description
import com.pandulapeter.campfire.presentation.resources.settings_song_details_lookup
import com.pandulapeter.campfire.presentation.resources.settings_song_details_lookup_description
import com.pandulapeter.campfire.presentation.resources.settings_songs_description
import com.pandulapeter.campfire.presentation.resources.settings_songs_description_without_setlists
import com.pandulapeter.campfire.presentation.resources.songs
import com.pandulapeter.campfire.presentation.resources.songs_random_picks
import com.pandulapeter.campfire.presentation.resources.settings_tuner_description
import com.pandulapeter.campfire.presentation.resources.tuner
import com.pandulapeter.campfire.presentation.ui.state.PreferencesController
import com.pandulapeter.campfire.presentation.ui.components.SwitchListItem
import com.pandulapeter.campfire.presentation.ui.components.WarningCard
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.navigation.FeatureSwitches
import com.pandulapeter.campfire.presentation.ui.settingsPage.AnimatedSettingsRow
import com.pandulapeter.campfire.presentation.ui.settingsPage.SettingsSection

/**
 * The parts of the app a reader can do without, each a switch that takes it out of every screen it is on, so that a
 * singer is not handed a metronome and a band that plays from one list is not handed a setlist tab. They follow the
 * tabs of the navigation in their order - songs, setlists, metronome, tuner, chords, scales - and how a song is read is
 * none of theirs: that is the Song and the Chords tabs'. The two switches that let the app reach the network
 * are a group of their own, so that what can make a request is found in one place rather than among the rest.
 */
@Composable
internal fun FeaturesSections(
    preferencesController: PreferencesController,
    userPreferences: UserPreferences?,
) {
    PlayingSection(preferencesController = preferencesController, userPreferences = userPreferences)
    OnlineSection(preferencesController = preferencesController, userPreferences = userPreferences)
}

@Composable
private fun PlayingSection(
    preferencesController: PreferencesController,
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
        onCheckedChange = preferencesController::setSongsEnabled,
    )
    // Disabled with the songs off, as the setlists are: the picks are a part of the songs list.
    SwitchListItem(
        title = stringResource(Res.string.songs_random_picks),
        description = stringResource(Res.string.settings_random_picks_description),
        isChecked = userPreferences?.isRandomPicksEnabled != false,
        isEnabled = userPreferences?.areSongsEnabled != false,
        onCheckedChange = preferencesController::setRandomPicksEnabled,
    )
    // Disabled rather than hidden with the songs off: a setlist is a list of songs, so without them there is nothing it
    // could hold, and what it is set to is still what the app has once the songs are back.
    SwitchListItem(
        title = stringResource(Res.string.setlists),
        description = stringResource(Res.string.settings_setlists_description),
        isChecked = userPreferences?.areSetlistsEnabled != false,
        isEnabled = userPreferences?.areSongsEnabled != false,
        onCheckedChange = preferencesController::setSetlistsEnabled,
    )
    SwitchListItem(
        title = stringResource(Res.string.metronome),
        description = stringResource(Res.string.settings_metronome_description),
        isChecked = userPreferences?.isMetronomeEnabled != false,
        onCheckedChange = preferencesController::setMetronomeEnabled,
    )
    // Off, nothing in the app can ask for the microphone.
    SwitchListItem(
        title = stringResource(Res.string.tuner),
        description = stringResource(Res.string.settings_tuner_description),
        isChecked = userPreferences?.isTunerEnabled != false,
        onCheckedChange = preferencesController::setTunerEnabled,
    )
    // The tab only: a song's chords and their diagrams are how it is read (`SongPreferencesPage`), and a singer reading the
    // lyrics alone can still look a chord up for the band.
    SwitchListItem(
        title = stringResource(Res.string.chords),
        description = stringResource(Res.string.settings_chord_library_description),
        isChecked = userPreferences?.isChordLibraryEnabled != false,
        onCheckedChange = preferencesController::setChordLibraryEnabled,
    )
    SwitchListItem(
        title = stringResource(Res.string.scales),
        description = stringResource(Res.string.settings_scales_description),
        isChecked = userPreferences?.areScalesEnabled != false,
        onCheckedChange = preferencesController::setScalesEnabled,
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
    preferencesController: PreferencesController,
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
        onCheckedChange = preferencesController::setSongDetailsLookupEnabled,
    )
    // Off, no cover is fetched from anywhere, which is what somebody who does not want a song file to make the app
    // contact a host turns it off for.
    SwitchListItem(
        title = stringResource(Res.string.settings_cover_art),
        description = stringResource(Res.string.settings_cover_art_description),
        isChecked = userPreferences?.isCoverArtEnabled == true,
        isEnabled = areSongsEnabled,
        onCheckedChange = preferencesController::setCoverArtEnabled,
    )
}
