/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.metronome.api.model.BeatLevel
import com.pandulapeter.campfire.metronome.api.model.MetronomePlayback
import com.pandulapeter.campfire.metronome.api.model.TimeSignature
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.metronome.beatLevelsOf
import com.pandulapeter.campfire.presentation.ui.metronome.metronomeTimeSignatureOf

/**
 * What [com.pandulapeter.campfire.presentation.ui.metronome.MetronomePanel] shows, for the two screens that have one.
 *
 * @param timeSignature The bar the click counts, which the accents tapped on the panel are stored under.
 */
@Immutable
internal data class MetronomePanelState(
    val timeSignature: TimeSignature,
    val beatLevels: List<BeatLevel>,
    val isPlaying: Boolean,
    val isFlashEnabled: Boolean,
)

/**
 * The [MetronomePanelState] of [viewModel], collected in the composition rather than read off the view model's flows'
 * current values, so that a `{time}` saved or synced while the panel is up redraws the bar, and in the same frame as the
 * pager's page change that makes another song the click's.
 */
@Composable
internal fun rememberMetronomePanelState(viewModel: CampfireViewModel): MetronomePanelState {
    val playback by viewModel.metronomePlayback.collectAsStateWithLifecycle()
    val settings by viewModel.metronomeSettings.collectAsStateWithLifecycle()
    val songsByFileName by viewModel.songsByFileName.collectAsStateWithLifecycle()
    val songsBeingRenamed by viewModel.songsBeingRenamed.collectAsStateWithLifecycle()
    val timeSignature = metronomeTimeSignatureOf(viewModel.metronomeContext, settings) { songsByFileName[it] ?: songsBeingRenamed[it] }
    return MetronomePanelState(
        timeSignature = timeSignature,
        beatLevels = settings.beatLevelsOf(timeSignature),
        isPlaying = playback is MetronomePlayback.Playing,
        isFlashEnabled = settings.isVisualBeatEnabled,
    )
}
