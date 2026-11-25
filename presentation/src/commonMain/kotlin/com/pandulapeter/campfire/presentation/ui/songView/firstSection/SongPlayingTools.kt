/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songView.firstSection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember

/**
 * The tools a song is played with on song details, in its dock rather than the app bar: the tuner and the tempo chip.
 * They are there in read only mode too, since neither changes anything about a song. The dock is there wherever the
 * chords or the metronome are switched on, so only with both off is there nowhere for the tuner, which the menu then
 * holds.
 */
@Immutable
internal class SongPlayingTools(
    /** Null with the tuner switched off. */
    val onTunerClicked: (() -> Unit)?,
    /** Whether the tuner sheet is open over the song, which draws its button as switched on. */
    val isTunerSheetShown: Boolean,
    /** Null with the metronome switched off. */
    val metronome: SongMetronomeTool?,
)

/**
 * What song details' tempo chip says and opens: [bpm], the tempo of the stretch the page is in, and [onTempoClicked], the
 * Tempo sheet, null in read only mode, whose chip opens nothing.
 */
@Immutable
internal class SongMetronomeTool(
    val bpm: Int,
    val onTempoClicked: (() -> Unit)?,
)

/**
 * The tools of one page of the song details pager, [bpm] being the tempo of the stretch the page is in.
 *
 * @return Null where both are switched off.
 */
@Composable
internal fun rememberSongPlayingTools(
    onTunerClicked: () -> Unit,
    bpm: Int,
    isMetronomeEnabled: Boolean,
    isTunerEnabled: Boolean,
    isTunerSheetShown: Boolean,
    onTempoClicked: (() -> Unit)?,
): SongPlayingTools? = remember(onTunerClicked, bpm, isMetronomeEnabled, isTunerEnabled, isTunerSheetShown, onTempoClicked) {
    if (!isMetronomeEnabled && !isTunerEnabled) {
        null
    } else {
        SongPlayingTools(
            onTunerClicked = if (isTunerEnabled) onTunerClicked else null,
            isTunerSheetShown = isTunerSheetShown,
            metronome = if (isMetronomeEnabled) SongMetronomeTool(bpm = bpm, onTempoClicked = onTempoClicked) else null,
        )
    }
}
