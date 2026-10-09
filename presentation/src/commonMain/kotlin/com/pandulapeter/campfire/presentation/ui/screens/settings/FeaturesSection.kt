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

import androidx.compose.runtime.Composable
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.metronome
import com.pandulapeter.campfire.presentation.resources.setlists
import com.pandulapeter.campfire.presentation.resources.settings_chords
import com.pandulapeter.campfire.presentation.resources.settings_chord_diagrams
import com.pandulapeter.campfire.presentation.resources.settings_chord_diagrams_description
import com.pandulapeter.campfire.presentation.resources.settings_chords_description
import com.pandulapeter.campfire.presentation.resources.settings_cover_art
import com.pandulapeter.campfire.presentation.resources.settings_cover_art_description
import com.pandulapeter.campfire.presentation.resources.settings_metronome_description
import com.pandulapeter.campfire.presentation.resources.settings_read_only_mode
import com.pandulapeter.campfire.presentation.resources.settings_read_only_mode_description
import com.pandulapeter.campfire.presentation.resources.settings_setlists_description
import com.pandulapeter.campfire.presentation.resources.settings_tuner_description
import com.pandulapeter.campfire.presentation.resources.tuner
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.SwitchListItem

/**
 * The parts of the app a reader can do without, each a switch that takes it out of every screen it is on, so that a
 * singer is not handed a metronome and a band that plays from one list is not handed a setlist tab. Read only mode is
 * first: it is the one that decides what the rest of the app is still allowed to do. Then what a song page shows, the
 * most musical first, then the tab that is only a tab, and last the one switch that decides whether the app reaches the
 * network on its own.
 */
@Composable
internal fun FeaturesSection(
    viewModel: CampfireViewModel,
    userPreferences: UserPreferences?,
    isPerformanceModeEnabled: Boolean,
) = SettingsSection {
    SwitchListItem(
        title = stringResource(Res.string.settings_read_only_mode),
        description = stringResource(Res.string.settings_read_only_mode_description),
        isChecked = isPerformanceModeEnabled,
        onCheckedChange = viewModel::setPerformanceModeEnabled,
    )
    SwitchListItem(
        title = stringResource(Res.string.settings_chords),
        description = stringResource(Res.string.settings_chords_description),
        isChecked = userPreferences?.areChordsEnabled != false,
        onCheckedChange = viewModel::setChordsEnabled,
    )
    // Disabled rather than hidden with the chords off, like the chord spelling: there is nothing to finger then, and
    // what it is set to is still what the songs will open with once the chords are back.
    SwitchListItem(
        title = stringResource(Res.string.settings_chord_diagrams),
        description = stringResource(Res.string.settings_chord_diagrams_description),
        isChecked = userPreferences?.areChordDiagramsEnabled != false,
        isEnabled = userPreferences?.areChordsEnabled != false,
        onCheckedChange = viewModel::setChordDiagramsEnabled,
    )
    SwitchListItem(
        title = stringResource(Res.string.metronome),
        description = stringResource(Res.string.settings_metronome_description),
        isChecked = userPreferences?.isMetronomeEnabled != false,
        onCheckedChange = viewModel::setMetronomeEnabled,
    )
    // Off, nothing in the app can ask for the microphone.
    SwitchListItem(
        title = stringResource(Res.string.tuner),
        description = stringResource(Res.string.settings_tuner_description),
        isChecked = userPreferences?.isTunerEnabled != false,
        onCheckedChange = viewModel::setTunerEnabled,
    )
    SwitchListItem(
        title = stringResource(Res.string.setlists),
        description = stringResource(Res.string.settings_setlists_description),
        isChecked = userPreferences?.areSetlistsEnabled != false,
        onCheckedChange = viewModel::setSetlistsEnabled,
    )
    // Off, no cover is fetched from anywhere, which is what somebody who does not want a song file to make the app
    // contact a host turns it off for.
    SwitchListItem(
        title = stringResource(Res.string.settings_cover_art),
        description = stringResource(Res.string.settings_cover_art_description),
        isChecked = userPreferences?.isCoverArtEnabled == true,
        onCheckedChange = viewModel::setCoverArtEnabled,
    )
}
