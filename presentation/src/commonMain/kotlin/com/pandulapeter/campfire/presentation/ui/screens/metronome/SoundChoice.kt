/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.metronome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.metronome.api.model.MetronomeSound
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.metronome_sound_beep
import com.pandulapeter.campfire.presentation.resources.metronome_sound_click
import com.pandulapeter.campfire.presentation.resources.metronome_sound_cowbell
import com.pandulapeter.campfire.presentation.resources.metronome_sound_hi_hat
import com.pandulapeter.campfire.presentation.resources.metronome_sound_woodblock
import com.pandulapeter.campfire.presentation.ui.components.CHIP_GAP
import com.pandulapeter.campfire.presentation.ui.components.SelectableChip

/** What every click sounds like, a song's included; the caller plays a sound as it is tapped. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SoundChoice(
    selected: MetronomeSound,
    onSelected: (MetronomeSound) -> Unit,
) = FlowRow(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    horizontalArrangement = Arrangement.spacedBy(CHIP_GAP),
    verticalArrangement = Arrangement.spacedBy(CHIP_GAP),
) {
    MetronomeSound.entries.forEach { sound ->
        SelectableChip(
            isSelected = sound == selected,
            role = Role.RadioButton,
            onClick = { onSelected(sound) },
        ) { contentColor ->
            Text(
                text = sound.label(),
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
            )
        }
    }
}

@Composable
private fun MetronomeSound.label() = stringResource(
    when (this) {
        MetronomeSound.CLICK -> Res.string.metronome_sound_click
        MetronomeSound.WOODBLOCK -> Res.string.metronome_sound_woodblock
        MetronomeSound.BEEP -> Res.string.metronome_sound_beep
        MetronomeSound.HI_HAT -> Res.string.metronome_sound_hi_hat
        MetronomeSound.COWBELL -> Res.string.metronome_sound_cowbell
    }
)
