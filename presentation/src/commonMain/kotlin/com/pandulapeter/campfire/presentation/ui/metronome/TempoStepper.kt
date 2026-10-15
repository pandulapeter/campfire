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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import com.pandulapeter.campfire.metronome.api.TapTempo
import com.pandulapeter.campfire.metronome.api.model.MetronomePattern
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_add
import com.pandulapeter.campfire.presentation.resources.ic_subtract
import com.pandulapeter.campfire.presentation.resources.ic_tap_tempo
import com.pandulapeter.campfire.presentation.resources.metronome_tap_description
import com.pandulapeter.campfire.presentation.resources.song_details_tempo
import com.pandulapeter.campfire.presentation.resources.song_details_tempo_decrease
import com.pandulapeter.campfire.presentation.resources.song_details_tempo_increase
import com.pandulapeter.campfire.presentation.resources.song_details_tempo_reset
import com.pandulapeter.campfire.presentation.ui.components.controls.STEPPER_HEIGHT
import com.pandulapeter.campfire.presentation.ui.components.controls.STEPPER_ICON_SIZE
import com.pandulapeter.campfire.presentation.ui.components.controls.STEPPER_SEGMENT_PADDING
import com.pandulapeter.campfire.presentation.ui.components.controls.Stepper
import com.pandulapeter.campfire.presentation.ui.components.controls.StepperEntry
import org.jetbrains.compose.resources.painterResource

/**
 * The tempo stepper of a song, the transposition's twin gesture for gesture: highlighted while the song plays at a
 * tempo of its own here, and its reset mark puts it back to the file's. Its buttons repeat while held, since a
 * tempo is often far from where it starts. The Metronome tab steps its own tempo with the same pill, so that a tempo is
 * set the same way wherever it is set — always plainly and with no reset there, since that tempo is nobody's override.
 *
 * @param isDefault Whether [bpm] is the one the reset mark would put back, which is when the value is drawn plainly.
 * @param valueKey What a new tempo cross-fades in for, see [Stepper]: every change by default, and something that stays
 * the same across the changes that come many to a second where the caller has such a source, a slider being dragged.
 * @param onSet Sets a tempo tapped in or typed: the pill ends in a Tap segment, since tapping along with a song sets the
 * very number the stepper steps, so the two are one control rather than a stepper with a loose word of a button beside
 * it; and its value is a field the number is typed into ([StepperEntry]), a tempo being a long way to step to.
 */
@Composable
internal fun TempoStepper(
    modifier: Modifier = Modifier,
    bpm: Int,
    isDefault: Boolean,
    fontScale: Float = 1f,
    height: Dp = STEPPER_HEIGHT,
    valueKey: Any = bpm,
    onStep: (delta: Int) -> Unit,
    onSet: (bpm: Int) -> Unit,
    onReset: (() -> Unit)?,
) = Stepper(
    modifier = modifier,
    value = bpm.toString(),
    valueDescription = stringResource(Res.string.song_details_tempo, bpm.toString()),
    fontScale = fontScale,
    height = height,
    valueKey = valueKey,
    widestValues = listOf(MetronomePattern.BPM_RANGE.first.toString(), MetronomePattern.BPM_RANGE.last.toString()),
    isDefault = isDefault,
    decreaseIcon = painterResource(Res.drawable.ic_subtract),
    decreaseLabel = stringResource(Res.string.song_details_tempo_decrease),
    canDecrease = bpm > MetronomePattern.BPM_RANGE.first,
    onDecrease = { onStep(-1) },
    increaseIcon = painterResource(Res.drawable.ic_add),
    increaseLabel = stringResource(Res.string.song_details_tempo_increase),
    canIncrease = bpm < MetronomePattern.BPM_RANGE.last,
    onIncrease = { onStep(1) },
    resetLabel = onReset?.let { stringResource(Res.string.song_details_tempo_reset) },
    onReset = onReset,
    repeatsOnHold = true,
    entry = StepperEntry(number = bpm, range = MetronomePattern.BPM_RANGE, onEntered = onSet),
    trailing = { TapTempoSegment(fontScale = fontScale, onTempo = onSet) },
)

/**
 * Tap tempo as the last segment of the tempo stepper's own pill, see [TempoStepper]. It is as tall as the pill and
 * takes the press over its whole padding, so that the segment and the stepper's buttons are hit the same way. A tap is
 * not felt: it is tapped along with music, and a buzz on every beat would compete with the beat being tapped.
 */
@Composable
private fun TapTempoSegment(
    fontScale: Float,
    onTempo: (bpm: Int) -> Unit,
) {
    val tapTempo = remember { TapTempo() }
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .clickable(role = Role.Button) {
                tapTempo.tap()?.let(onTempo)
            }
            .padding(horizontal = STEPPER_SEGMENT_PADDING),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            modifier = Modifier.size(STEPPER_ICON_SIZE * fontScale),
            painter = painterResource(Res.drawable.ic_tap_tempo),
            contentDescription = stringResource(Res.string.metronome_tap_description),
        )
    }
}
