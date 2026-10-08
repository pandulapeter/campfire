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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.metronome.api.model.TimeSignature
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_add
import com.pandulapeter.campfire.presentation.resources.ic_subtract
import com.pandulapeter.campfire.presentation.resources.metronome_beat_unit_decrease
import com.pandulapeter.campfire.presentation.resources.metronome_beat_unit_increase
import com.pandulapeter.campfire.presentation.resources.metronome_beats_decrease
import com.pandulapeter.campfire.presentation.resources.metronome_beats_increase
import com.pandulapeter.campfire.presentation.ui.components.CHIP_GAP
import com.pandulapeter.campfire.presentation.ui.components.SelectableChip
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.Stepper
import org.jetbrains.compose.resources.painterResource

/**
 * The time signature as bars to pick from: the common ones as chips, and two steppers under them for every other bar,
 * side by side with a slash between them, so that the pair reads as the signature it sets - the beats of the bar over
 * the note that counts as one - and needs no label of its own. Shared by the Metronome tab, where it sets the tab's own
 * signature, and by the "Song defaults" sheet that writes a song's `{time}`, so that a bar is picked the same way
 * wherever it is picked.
 *
 * @param horizontalPadding Where the chips and the steppers start: the tab's own margin by default, nothing in a form
 * that already pads its fields.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TimeSignaturePicker(
    modifier: Modifier = Modifier,
    timeSignature: TimeSignature,
    horizontalPadding: Dp = 16.dp,
    onChange: (TimeSignature) -> Unit,
) = Column(modifier = modifier) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(CHIP_GAP),
        verticalArrangement = Arrangement.spacedBy(CHIP_GAP),
    ) {
        COMMON_TIME_SIGNATURES.forEach { common ->
            SelectableChip(
                isSelected = common == timeSignature,
                role = Role.RadioButton,
                onClick = { onChange(common) },
            ) { contentColor ->
                Text(
                    text = common.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor,
                )
            }
        }
    }
    Row(
        modifier = Modifier.padding(start = horizontalPadding, top = TIME_SIGNATURE_STEPPERS_GAP, end = horizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(TIME_SIGNATURE_SLASH_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Stepper(
            value = timeSignature.beats.toString(),
            isDefault = true,
            decreaseIcon = painterResource(Res.drawable.ic_subtract),
            decreaseLabel = stringResource(Res.string.metronome_beats_decrease),
            canDecrease = timeSignature.beats > TimeSignature.BEATS_RANGE.first,
            onDecrease = { onChange(timeSignature.copy(beats = timeSignature.beats - 1)) },
            increaseIcon = painterResource(Res.drawable.ic_add),
            increaseLabel = stringResource(Res.string.metronome_beats_increase),
            canIncrease = timeSignature.beats < TimeSignature.BEATS_RANGE.last,
            onIncrease = { onChange(timeSignature.copy(beats = timeSignature.beats + 1)) },
            resetLabel = null,
            onReset = null,
        )
        Text(
            text = TIME_SIGNATURE_SLASH,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val unitIndex = TimeSignature.UNITS.indexOf(timeSignature.unit)
        Stepper(
            value = timeSignature.unit.toString(),
            isDefault = true,
            decreaseIcon = painterResource(Res.drawable.ic_subtract),
            decreaseLabel = stringResource(Res.string.metronome_beat_unit_decrease),
            canDecrease = unitIndex > 0,
            onDecrease = { onChange(timeSignature.copy(unit = TimeSignature.UNITS[unitIndex - 1])) },
            increaseIcon = painterResource(Res.drawable.ic_add),
            increaseLabel = stringResource(Res.string.metronome_beat_unit_increase),
            canIncrease = unitIndex < TimeSignature.UNITS.lastIndex,
            onIncrease = { onChange(timeSignature.copy(unit = TimeSignature.UNITS[unitIndex + 1])) },
            resetLabel = null,
            onReset = null,
        )
    }
}

/** The slash a time signature is written with, between the two steppers of [TimeSignaturePicker] as between its numbers. */
private const val TIME_SIGNATURE_SLASH = "/"
private val TIME_SIGNATURE_SLASH_GAP = 12.dp

/**
 * What the steppers of [TimeSignaturePicker] keep free under the chips: more than the [CHIP_GAP] between two rows of
 * them, so that the pair reads as a control of its own rather than as one more row of chips.
 */
private val TIME_SIGNATURE_STEPPERS_GAP = 12.dp

/** The bars most songs are in, offered as chips so that the two steppers are only needed for the rest. */
private val COMMON_TIME_SIGNATURES = listOf(
    TimeSignature(2, 4),
    TimeSignature(3, 4),
    TimeSignature(4, 4),
    TimeSignature(6, 8),
    TimeSignature(7, 8),
    TimeSignature(12, 8),
)
