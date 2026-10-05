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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.metronome.api.model.MetronomePlayback
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.metronome_start
import com.pandulapeter.campfire.presentation.resources.metronome_stop
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.ScrollPosition
import com.pandulapeter.campfire.presentation.ui.components.only
import com.pandulapeter.campfire.presentation.ui.components.rememberRetainedScrollState
import com.pandulapeter.campfire.presentation.ui.metronome.PlayStopMark
import com.pandulapeter.campfire.presentation.ui.screens.settings.SettingsPage
import com.pandulapeter.campfire.presentation.ui.screens.settings.SettingsWidthLayout

/**
 * The whole instrument: the tempo and the beat row in one section, how it is counted and how it sounds in the other,
 * side by side where the window has the room for both and stacked where it does not, the way a settings tab is laid
 * out ([SettingsPage]). Play and stop is the screen's floating action button.
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
    Box(
        // A metronome is practised with both hands on the instrument, and a screen that dims and locks takes the beat row
        // with it.
        modifier = modifier.fillMaxSize().then(if (isPlaying) Modifier.keepScreenOn() else Modifier),
    ) {
        SettingsPage(
            sectionColumns = layout.sectionColumns,
            scrollState = scrollState,
            contentPadding = contentPadding.only(start = true, end = true, bottom = true, extraBottom = PLAY_BUTTON_CLEARANCE),
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
        val label = stringResource(if (isPlaying) Res.string.metronome_stop else Res.string.metronome_start)
        FloatingActionButton(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(contentPadding.only(end = true, bottom = true))
                .padding(16.dp)
                .semantics { contentDescription = label },
            onClick = viewModel::toggleMetronome,
        ) {
            PlayStopMark(isPlaying = isPlaying)
        }
    }
}

/** What the end of the scrolling content keeps free, so that its last row is never under the play button. */
private val PLAY_BUTTON_CLEARANCE = 72.dp
