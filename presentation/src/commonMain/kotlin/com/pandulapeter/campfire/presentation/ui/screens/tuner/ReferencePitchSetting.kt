/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.tuner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.chordpro.chords.ChordNotation
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_add
import com.pandulapeter.campfire.presentation.resources.ic_subtract
import com.pandulapeter.campfire.presentation.resources.tuner_play_reference
import com.pandulapeter.campfire.presentation.resources.tuner_reference_pitch
import com.pandulapeter.campfire.presentation.resources.tuner_reference_pitch_decrease
import com.pandulapeter.campfire.presentation.resources.tuner_reference_pitch_increase
import com.pandulapeter.campfire.presentation.resources.tuner_reference_pitch_reset
import com.pandulapeter.campfire.presentation.resources.tuner_reference_pitch_value
import com.pandulapeter.campfire.presentation.ui.components.controls.LabeledControlRow
import com.pandulapeter.campfire.presentation.ui.components.controls.Stepper
import com.pandulapeter.campfire.presentation.ui.components.controls.StepperEntry
import com.pandulapeter.campfire.presentation.ui.metronome.PlayStopMark
import com.pandulapeter.campfire.presentation.ui.tuner.REFERENCE_NOTE
import com.pandulapeter.campfire.presentation.ui.tuner.noteName
import com.pandulapeter.campfire.presentation.ui.tuner.noteNameWithOctave
import com.pandulapeter.campfire.tuner.api.Pitch
import org.jetbrains.compose.resources.painterResource

/**
 * The frequency of A4 everything is tuned from, stepped by a hertz or typed the way the tempo is (and reset to 440 from
 * the focused value the same way), next to a toggle that plays that A until it is tapped again. The label names the note
 * and the pill holds only the frequency, which keeps the pill narrow enough to stand beside its label on a phone.
 */
@Composable
internal fun ReferencePitchSetting(
    referencePitch: Int,
    notation: ChordNotation,
    isReferenceTonePlaying: Boolean,
    onReferencePitchChanged: (Int) -> Unit,
    onToggleReferenceTone: () -> Unit,
) {
    LabeledControlRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        label = {
            Text(
                text = stringResource(Res.string.tuner_reference_pitch, noteNameWithOctave(REFERENCE_NOTE, notation)),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
    ) {
        ReferencePitchControls(
            referencePitch = referencePitch,
            notation = notation,
            isReferenceTonePlaying = isReferenceTonePlaying,
            onReferencePitchChanged = onReferencePitchChanged,
            onToggleReferenceTone = onToggleReferenceTone,
        )
    }
}

/** The stepper and the tone toggle of [ReferencePitchSetting]. */
@Composable
private fun ReferencePitchControls(
    referencePitch: Int,
    notation: ChordNotation,
    isReferenceTonePlaying: Boolean,
    onReferencePitchChanged: (Int) -> Unit,
    onToggleReferenceTone: () -> Unit,
) = Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
) {
    Stepper(
        value = stringResource(Res.string.tuner_reference_pitch_value, referencePitch),
        widestValues = Pitch.REFERENCE_PITCH_RANGE.let { listOf(it.first, it.last) }.map { stringResource(Res.string.tuner_reference_pitch_value, it) },
        isDefault = referencePitch == Pitch.DEFAULT_REFERENCE_PITCH,
        decreaseIcon = painterResource(Res.drawable.ic_subtract),
        decreaseLabel = stringResource(Res.string.tuner_reference_pitch_decrease),
        canDecrease = referencePitch > Pitch.REFERENCE_PITCH_RANGE.first,
        onDecrease = { onReferencePitchChanged(referencePitch - 1) },
        increaseIcon = painterResource(Res.drawable.ic_add),
        increaseLabel = stringResource(Res.string.tuner_reference_pitch_increase),
        canIncrease = referencePitch < Pitch.REFERENCE_PITCH_RANGE.last,
        onIncrease = { onReferencePitchChanged(referencePitch + 1) },
        resetLabel = stringResource(Res.string.tuner_reference_pitch_reset),
        onReset = { onReferencePitchChanged(Pitch.DEFAULT_REFERENCE_PITCH) },
        repeatsOnHold = true,
        entry = StepperEntry(number = referencePitch, range = Pitch.REFERENCE_PITCH_RANGE, onEntered = onReferencePitchChanged),
    )
    val description = stringResource(Res.string.tuner_play_reference, noteName(REFERENCE_NOTE, notation))
    FilledTonalIconToggleButton(
        checked = isReferenceTonePlaying,
        onCheckedChange = { onToggleReferenceTone() },
    ) {
        PlayStopMark(
            modifier = Modifier.semantics { contentDescription = description },
            isPlaying = isReferenceTonePlaying,
            size = 18.dp,
            color = LocalContentColor.current,
        )
    }
}
