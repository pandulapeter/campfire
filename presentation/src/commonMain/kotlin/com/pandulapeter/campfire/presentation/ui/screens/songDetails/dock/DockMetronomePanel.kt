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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.song_details_tempo
import com.pandulapeter.campfire.presentation.ui.metronome.BeatRow
import com.pandulapeter.campfire.presentation.ui.metronome.TempoStepper

/**
 * The dock where the metronome is all it holds (neither diagrams nor scale suggestions): the metronome panel itself,
 * [height] tall, since there is room for it, padded in from the screen's edges by [horizontalPadding]: the tempo stepper
 * with Tap (read only mode's number alone, which sets nothing), the bar as a row of beats as wide as the rest leaves,
 * whose accents are tapped in place (not in read only mode, where they would be written), and play and stop at the end;
 * after [start] where it is given (the one-line instrument card of an instrument without shapes).
 */
@Composable
internal fun DockMetronomePanel(
    modifier: Modifier = Modifier,
    tempo: DockTempo,
    metronome: DockMetronome,
    height: Dp,
    horizontalPadding: PaddingValues,
    start: (@Composable () -> Unit)? = null,
) = Row(
    modifier = modifier
        .fillMaxWidth()
        .height(height)
        .padding(horizontalPadding),
    horizontalArrangement = Arrangement.spacedBy(DOCK_ITEM_GAP),
    verticalAlignment = Alignment.CenterVertically,
) {
    start?.invoke()
    // Read only mode's number, which sets nothing, and the stepper crossfade into each other as the mode is switched.
    AnimatedContent(
        targetState = tempo.control,
        contentKey = { it != null },
        transitionSpec = { fadeIn() togetherWith fadeOut() using SizeTransform(clip = false) },
        contentAlignment = Alignment.CenterStart,
    ) { control ->
        if (control != null) {
            TempoStepper(
                bpm = control.tempo.bpm,
                isDefault = control.tempo.isDefault,
                height = height,
                onStep = { delta ->
                    println("Dock: tempo stepped")
                    control.onStep(delta)
                },
                onSet = { bpm ->
                    println("Dock: tempo set")
                    control.onSet(bpm)
                },
                onReset = {
                    println("Dock: tempo reset")
                    control.onReset()
                },
            )
        } else {
            Text(
                text = stringResource(Res.string.song_details_tempo, tempo.bpm.toString()),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
            )
        }
    }
    val panel = metronome.panel
    BeatRow(
        modifier = Modifier.weight(1f),
        beatLevels = panel.beatLevels,
        beats = metronome.beats,
        isPlaying = panel.isPlaying,
        isFlashEnabled = panel.isFlashEnabled,
        blockHeight = height - BEATS_INSET * 2,
        blockGap = BEAT_GAP,
        onBeatLevelsChanged = metronome.onBeatLevelsChanged.takeIf { tempo.control != null },
    )
    DockPlayStop(isPlaying = panel.isPlaying, onClick = metronome.onPlayStop)
}

private val BEATS_INSET = 4.dp
private val BEAT_GAP = 3.dp
