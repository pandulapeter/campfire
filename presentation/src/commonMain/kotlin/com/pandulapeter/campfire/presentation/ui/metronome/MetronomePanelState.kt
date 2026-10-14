/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.metronome

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.metronome.api.model.BeatLevel
import com.pandulapeter.campfire.metronome.api.model.MetronomePlayback
import com.pandulapeter.campfire.metronome.api.model.TimeSignature
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel

/**
 * What [com.pandulapeter.campfire.presentation.ui.metronome.MetronomePanel] shows, for the two screens that have one.
 *
 * @param timeSignature The bar the click counts, which the accents tapped on the panel are stored under: the song's own on
 *   a song, the signature's on the Metronome tab.
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
 * pager's page change that makes another song the click's. A playing click draws the bar that is heard instead: the
 * song paged to waits for the end of the bar, and only then does the row take its signature. While a change to another
 * signature waits, the accents drawn are the heard bar's own too, since the click finishes it with them.
 */
@Composable
internal fun rememberMetronomePanelState(viewModel: CampfireViewModel): MetronomePanelState {
    val playback by viewModel.metronomePlayback.collectAsStateWithLifecycle()
    val settings by viewModel.metronomeSettings.collectAsStateWithLifecycle()
    val songsByFileName by viewModel.songsByFileName.collectAsStateWithLifecycle()
    val songsBeingRenamed by viewModel.songsBeingRenamed.collectAsStateWithLifecycle()
    val songBeatLevels by viewModel.songBeatLevels.collectAsStateWithLifecycle()
    val context = viewModel.metronomeContext
    val timeSignature = (playback as? MetronomePlayback.Playing)?.pattern?.timeSignature
        ?: metronomeTimeSignatureOf(context, settings) { songsByFileName[it] ?: songsBeingRenamed[it] }
    val barBeingLeft = playback.barBeingLeft
    return MetronomePanelState(
        timeSignature = timeSignature,
        beatLevels = barBeingLeft?.let { pattern -> List(pattern.timeSignature.beats) { index -> pattern.beatLevel(index) } }
            ?: settings.beatLevelsOf(timeSignature, songBeatLevels = (context as? MetronomeContext.Song)?.let { songBeatLevels[it.songFileName] }),
        isPlaying = playback is MetronomePlayback.Playing,
        isFlashEnabled = settings.isVisualBeatEnabled,
    )
}
