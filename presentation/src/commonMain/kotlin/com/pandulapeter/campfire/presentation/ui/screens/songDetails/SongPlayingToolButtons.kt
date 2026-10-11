/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.ui.metronome.MetronomeButton
import com.pandulapeter.campfire.presentation.ui.tuner.TunerButton

/**
 * The buttons of [tools] that [places] puts at [place], the tuner before the metronome, in a row of their own that ends
 * a group of the playing values. Emits nothing where none of them is there.
 */
@Composable
internal fun SongPlayingToolButtons(
    modifier: Modifier = Modifier,
    tools: SongPlayingTools,
    places: PlayingToolPlaces,
    place: PlayingToolPlace,
    height: Dp,
    fontScale: Float,
) {
    val onTunerClicked = tools.onTunerClicked?.takeIf { places.tuner == place }
    val metronome = tools.metronome?.takeIf { places.metronome == place }
    if (onTunerClicked != null || metronome != null) {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(PLAYING_TOOL_GAP),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onTunerClicked != null) {
                TunerButton(
                    isSheetShown = tools.isTunerSheetShown,
                    height = height,
                    fontScale = fontScale,
                    onClick = onTunerClicked,
                )
            }
            if (metronome != null) {
                MetronomeButton(
                    isPanelShown = metronome.isPanelShown,
                    playback = metronome.playback,
                    bpm = metronome.bpm,
                    beats = metronome.beats,
                    isFlashEnabled = metronome.isFlashEnabled,
                    height = height,
                    fontScale = fontScale,
                    onClick = metronome.onClick,
                )
            }
        }
    }
}

/** Between the two buttons where they stand together, the gap the playing controls keep. */
private val PLAYING_TOOL_GAP = 8.dp
