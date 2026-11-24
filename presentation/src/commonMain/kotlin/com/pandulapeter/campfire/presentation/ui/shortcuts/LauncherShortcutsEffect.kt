/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.shortcuts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.app_shortcut_paste_song
import com.pandulapeter.campfire.presentation.resources.app_shortcut_withdrawn
import com.pandulapeter.campfire.presentation.resources.chords
import com.pandulapeter.campfire.presentation.resources.metronome
import com.pandulapeter.campfire.presentation.resources.scales
import com.pandulapeter.campfire.presentation.resources.setlists
import com.pandulapeter.campfire.presentation.resources.songs_new_song
import com.pandulapeter.campfire.presentation.resources.tuner
import com.pandulapeter.campfire.presentation.ui.navigation.FeatureSwitches
import com.pandulapeter.campfire.presentation.ui.state.PreferencesController

/**
 * Hands [onChanged] the launcher shortcuts once the preferences are read, and again whenever the features, read only
 * mode, or the app's language change them: the labels are the composition's strings, and the system keeps whatever it
 * was last given, even after the app is gone.
 */
@Composable
internal fun LauncherShortcutsEffect(
    preferencesController: PreferencesController,
    onChanged: (LauncherShortcuts) -> Unit,
) {
    val userPreferences by preferencesController.userPreferences.collectAsStateWithLifecycle()
    val isReadOnly by preferencesController.isPerformanceModeEnabled.collectAsStateWithLifecycle()
    // Unread preferences would offer every shortcut a moment before the switches take some away.
    val preferences = userPreferences ?: return
    val offered = remember(preferences, isReadOnly) { LauncherShortcut.offered(features = FeatureSwitches.of(preferences), isReadOnly = isReadOnly) }
    val shortcuts = LauncherShortcuts(
        offered = offered.map { LauncherShortcuts.Labelled(shortcut = it, label = it.label()) },
        withdrawnMessage = stringResource(Res.string.app_shortcut_withdrawn),
    )
    val latestOnChanged by rememberUpdatedState(onChanged)
    LaunchedEffect(shortcuts) { latestOnChanged(shortcuts) }
}

@Composable
private fun LauncherShortcut.label() = stringResource(
    when (this) {
        LauncherShortcut.NEW_SONG -> Res.string.songs_new_song
        LauncherShortcut.PASTE_SONG -> Res.string.app_shortcut_paste_song
        LauncherShortcut.SETLISTS -> Res.string.setlists
        LauncherShortcut.METRONOME -> Res.string.metronome
        LauncherShortcut.TUNER -> Res.string.tuner
        LauncherShortcut.CHORDS -> Res.string.chords
        LauncherShortcut.SCALES -> Res.string.scales
    },
)
