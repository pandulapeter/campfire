/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import com.pandulapeter.campfire.metronome.api.Metronome
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.koin.compose.koinInject

/**
 * Tells the metronome whether a screen that can start a click is on top - the Metronome tab or a song, with the feature
 * on - which is where its output listens for the presses that allow a page's audio to start, so that tapping around the
 * rest of the app never opens the audio device. The screen composes before the tap that starts a click, so the listener
 * is there in time.
 */
@Composable
internal fun MetronomeStartableEffect(viewModel: CampfireViewModel) {
    val metronome = koinInject<Metronome>()
    LaunchedEffect(viewModel, metronome) {
        snapshotFlow { viewModel.backStack.lastOrNull() }
            .combine(viewModel.userPreferences.map { it?.isMetronomeEnabled != false }) { top, isEnabled ->
                isEnabled && (top == CampfireDestination.Metronome || top is CampfireDestination.SongDetails)
            }
            .distinctUntilChanged()
            .collect(metronome::setStartable)
    }
}
