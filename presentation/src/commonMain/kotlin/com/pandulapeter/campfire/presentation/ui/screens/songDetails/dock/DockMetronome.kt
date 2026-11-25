/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails.dock

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import com.pandulapeter.campfire.metronome.api.model.BeatLevel
import com.pandulapeter.campfire.metronome.api.model.MetronomeBeat
import com.pandulapeter.campfire.presentation.ui.metronome.MetronomeController
import com.pandulapeter.campfire.presentation.ui.metronome.MetronomePanelState
import com.pandulapeter.campfire.presentation.ui.metronome.rememberMetronomePanelState
import com.pandulapeter.campfire.presentation.ui.state.LibraryState
import com.pandulapeter.campfire.presentation.ui.state.songText.SongTextStore
import kotlinx.coroutines.flow.Flow

/**
 * The click as the dock draws it, the screen's rather than a page's: there is one click, and it plays for the page the
 * pager is on or heading for ([MetronomePanelState]: the bar it counts, its accents, and whether it plays, the bar being
 * heard while a page turn waits for its end). [beats] light the beat rows, [onPlayStop] starts and stops it, and
 * [onBeatLevelsChanged] writes the accents tapped on a full beat row (the metronome-only dock's).
 */
@Immutable
internal class DockMetronome(
    val panel: MetronomePanelState,
    val beats: Flow<MetronomeBeat>,
    val onPlayStop: () -> Unit,
    val onBeatLevelsChanged: (List<BeatLevel>) -> Unit,
)

/** The [DockMetronome] of [metronomeController], see [rememberMetronomePanelState]. */
@Composable
internal fun rememberDockMetronome(
    metronomeController: MetronomeController,
    libraryState: LibraryState,
    songTextStore: SongTextStore,
): DockMetronome {
    val panel = rememberMetronomePanelState(metronomeController, libraryState, songTextStore)
    return remember(metronomeController, panel) {
        DockMetronome(
            panel = panel,
            beats = metronomeController.metronomeBeats,
            onPlayStop = {
                println("Dock: click toggled")
                metronomeController.toggleMetronome()
            },
            onBeatLevelsChanged = { levels ->
                println("Dock: accent tapped")
                metronomeController.setBeatLevels(panel.timeSignature, levels)
            },
        )
    }
}
