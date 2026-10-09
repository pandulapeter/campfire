/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.tuner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.chordpro.ChordNotation
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
import com.pandulapeter.campfire.presentation.ui.components.LabeledControlRow
import com.pandulapeter.campfire.presentation.ui.components.SelectableChip
import com.pandulapeter.campfire.presentation.ui.components.Stepper
import com.pandulapeter.campfire.presentation.ui.metronome.PlayStopMark
import com.pandulapeter.campfire.tuner.api.Pitch
import org.jetbrains.compose.resources.painterResource

/**
 * The frequency of A4 everything is tuned from, stepped by a hertz the way the tempo is and reset to 440 by a tap on its
 * value, next to a chip that plays that A until it is tapped again.
 */
@Composable
internal fun ReferencePitchSetting(
    referencePitch: Int,
    notation: ChordNotation,
    isReferenceTonePlaying: Boolean,
    onReferencePitchChanged: (Int) -> Unit,
    onToggleReferenceTone: () -> Unit,
) {
    val referenceName = noteNameWithOctave(REFERENCE_NOTE, notation)
    LabeledControlRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        label = {
            Text(
                text = stringResource(Res.string.tuner_reference_pitch),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Stepper(
                value = stringResource(Res.string.tuner_reference_pitch_value, referenceName, referencePitch),
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
                onReset = { onReferencePitchChanged(Pitch.DEFAULT_REFERENCE_PITCH) }.takeIf { referencePitch != Pitch.DEFAULT_REFERENCE_PITCH },
                repeatsOnHold = true,
            )
            val description = stringResource(Res.string.tuner_play_reference)
            SelectableChip(
                modifier = Modifier.semantics { contentDescription = description },
                isSelected = isReferenceTonePlaying,
                role = Role.Switch,
                onClick = onToggleReferenceTone,
            ) { contentColor ->
                PlayStopMark(isPlaying = isReferenceTonePlaying, size = 18.dp, color = contentColor)
                Text(
                    modifier = Modifier.padding(start = 4.dp),
                    text = referenceName,
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor,
                )
            }
        }
    }
}

/** The note the reference pitch names, A4. */
internal const val REFERENCE_NOTE = 69
