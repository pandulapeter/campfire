/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.metronome

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.keepScreenOn
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.metronome.api.model.MetronomePlayback
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.ScrollPosition
import com.pandulapeter.campfire.presentation.ui.components.only
import com.pandulapeter.campfire.presentation.ui.components.rememberRetainedScrollState
import com.pandulapeter.campfire.presentation.ui.screens.settings.SettingsPage
import com.pandulapeter.campfire.presentation.ui.screens.settings.SettingsWidthLayout

/**
 * The rest of the instrument, next to the metronome panel that is always up while this tab is (`MetronomePanelScaffold`),
 * which is where the click is started and stopped and its tempo stepped: the tempo's slider and tap button and the
 * accents in one section, how it is counted and how it sounds in the other, side by side where the window has the room
 * for both and stacked where it does not, the way a settings tab is laid out ([SettingsPage]).
 *
 * The tab always shows and plays its own tempo and time signature: every way onto it clears the back stack, so no song
 * is open behind it, and a click it lent to a song is back on the tab's pattern by the time the tab is shown again.
 * Nothing here changes a song or a setlist, so performance mode leaves all of it in place.
 */
@Composable
internal fun MetronomeScreen(
    modifier: Modifier = Modifier,
    viewModel: CampfireViewModel,
    layout: SettingsWidthLayout,
    scrollPosition: ScrollPosition,
    contentPadding: PaddingValues,
) {
    val playback by viewModel.metronomePlayback.collectAsStateWithLifecycle()
    val isPlaying = playback is MetronomePlayback.Playing
    val scrollState = rememberRetainedScrollState(scrollPosition)
    SettingsPage(
        // A metronome is practised with both hands on the instrument, and a screen that dims and locks takes the beat row
        // with it.
        modifier = modifier.fillMaxSize().then(if (isPlaying) Modifier.keepScreenOn() else Modifier),
        sectionColumns = layout.sectionColumns,
        scrollState = scrollState,
        contentPadding = contentPadding.only(start = true, end = true, bottom = true),
        section = {
            MetronomeTempoSection(
                viewModel = viewModel,
                playback = playback,
            )
        },
        secondSection = {
            MetronomeOptionsSection(viewModel = viewModel)
        },
    )
}
