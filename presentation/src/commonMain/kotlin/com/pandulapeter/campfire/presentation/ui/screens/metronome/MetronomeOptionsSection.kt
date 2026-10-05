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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.metronome.api.model.MetronomeSound
import com.pandulapeter.campfire.metronome.api.model.Subdivision
import com.pandulapeter.campfire.metronome.api.model.TimeSignature
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_add
import com.pandulapeter.campfire.presentation.resources.ic_subtract
import com.pandulapeter.campfire.presentation.resources.metronome_beat_unit
import com.pandulapeter.campfire.presentation.resources.metronome_beat_unit_decrease
import com.pandulapeter.campfire.presentation.resources.metronome_beat_unit_increase
import com.pandulapeter.campfire.presentation.resources.metronome_beats_decrease
import com.pandulapeter.campfire.presentation.resources.metronome_beats_increase
import com.pandulapeter.campfire.presentation.resources.metronome_beats_per_bar
import com.pandulapeter.campfire.presentation.resources.metronome_flash
import com.pandulapeter.campfire.presentation.resources.metronome_mute
import com.pandulapeter.campfire.presentation.resources.metronome_mute_description
import com.pandulapeter.campfire.presentation.resources.metronome_sound
import com.pandulapeter.campfire.presentation.resources.metronome_sound_beep
import com.pandulapeter.campfire.presentation.resources.metronome_sound_click
import com.pandulapeter.campfire.presentation.resources.metronome_sound_cowbell
import com.pandulapeter.campfire.presentation.resources.metronome_sound_sticks
import com.pandulapeter.campfire.presentation.resources.metronome_sound_woodblock
import com.pandulapeter.campfire.presentation.resources.metronome_subdivision
import com.pandulapeter.campfire.presentation.resources.metronome_subdivision_eighths
import com.pandulapeter.campfire.presentation.resources.metronome_subdivision_none
import com.pandulapeter.campfire.presentation.resources.metronome_subdivision_sixteenths
import com.pandulapeter.campfire.presentation.resources.metronome_subdivision_triplets
import com.pandulapeter.campfire.presentation.resources.metronome_time_signature
import com.pandulapeter.campfire.presentation.resources.metronome_vibrate
import com.pandulapeter.campfire.presentation.resources.metronome_volume
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.SegmentedChoice
import com.pandulapeter.campfire.presentation.ui.components.SwitchListItem
import com.pandulapeter.campfire.presentation.ui.metronome.sound
import com.pandulapeter.campfire.presentation.ui.metronome.subdivision
import com.pandulapeter.campfire.presentation.ui.metronome.timeSignatureOrDefault
import com.pandulapeter.campfire.presentation.ui.platform.rememberBeatHaptics
import com.pandulapeter.campfire.presentation.ui.screens.settings.SettingsSection
import com.pandulapeter.campfire.presentation.ui.screens.settings.SettingsSubsection
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.MenuStepperRow
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.Stepper
import org.jetbrains.compose.resources.painterResource
import kotlin.math.roundToInt

/**
 * How the click is counted and how it sounds: the time signature (the common ones as chips, any other with the two
 * steppers under them), the subdivision, the sound (a tap on one plays it), the volume and the three switches. Only
 * the time signature is the tab's own; everything else is how every click sounds, a song's included.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MetronomeOptionsSection(
    modifier: Modifier = Modifier,
    viewModel: CampfireViewModel,
) = SettingsSection(modifier = modifier) {
    val settings by viewModel.metronomeSettings.collectAsStateWithLifecycle()
    val timeSignature = settings.timeSignatureOrDefault
    val setTimeSignature = { value: TimeSignature -> viewModel.updateMetronomeSettings { copy(timeSignature = value.toString()) } }
    SettingsSubsection(title = stringResource(Res.string.metronome_time_signature)) {
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            COMMON_TIME_SIGNATURES.forEach { common ->
                FilterChip(
                    selected = common == timeSignature,
                    onClick = { setTimeSignature(common) },
                    label = { Text(common.toString()) },
                )
            }
        }
        MenuStepperRow(label = stringResource(Res.string.metronome_beats_per_bar)) {
            Stepper(
                value = timeSignature.beats.toString(),
                isDefault = true,
                decreaseIcon = painterResource(Res.drawable.ic_subtract),
                decreaseLabel = stringResource(Res.string.metronome_beats_decrease),
                canDecrease = timeSignature.beats > TimeSignature.BEATS_RANGE.first,
                onDecrease = { setTimeSignature(timeSignature.copy(beats = timeSignature.beats - 1)) },
                increaseIcon = painterResource(Res.drawable.ic_add),
                increaseLabel = stringResource(Res.string.metronome_beats_increase),
                canIncrease = timeSignature.beats < TimeSignature.BEATS_RANGE.last,
                onIncrease = { setTimeSignature(timeSignature.copy(beats = timeSignature.beats + 1)) },
                resetLabel = null,
                onReset = null,
            )
        }
        val unitIndex = TimeSignature.UNITS.indexOf(timeSignature.unit)
        MenuStepperRow(label = stringResource(Res.string.metronome_beat_unit)) {
            Stepper(
                value = timeSignature.unit.toString(),
                isDefault = true,
                decreaseIcon = painterResource(Res.drawable.ic_subtract),
                decreaseLabel = stringResource(Res.string.metronome_beat_unit_decrease),
                canDecrease = unitIndex > 0,
                onDecrease = { setTimeSignature(timeSignature.copy(unit = TimeSignature.UNITS[unitIndex - 1])) },
                increaseIcon = painterResource(Res.drawable.ic_add),
                increaseLabel = stringResource(Res.string.metronome_beat_unit_increase),
                canIncrease = unitIndex < TimeSignature.UNITS.lastIndex,
                onIncrease = { setTimeSignature(timeSignature.copy(unit = TimeSignature.UNITS[unitIndex + 1])) },
                resetLabel = null,
                onReset = null,
            )
        }
    }
    SettingsSubsection(title = stringResource(Res.string.metronome_subdivision)) {
        SegmentedChoice(
            options = listOf(
                Subdivision.NONE to stringResource(Res.string.metronome_subdivision_none),
                Subdivision.EIGHTHS to stringResource(Res.string.metronome_subdivision_eighths),
                Subdivision.TRIPLETS to stringResource(Res.string.metronome_subdivision_triplets),
                Subdivision.SIXTEENTHS to stringResource(Res.string.metronome_subdivision_sixteenths),
            ),
            selected = settings.subdivision,
            isInline = true,
            onSelected = { viewModel.updateMetronomeSettings { copy(subdivisionId = it.id) } },
        )
    }
    SettingsSubsection(title = stringResource(Res.string.metronome_sound)) {
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MetronomeSound.entries.forEach { sound ->
                FilterChip(
                    selected = sound == settings.sound,
                    onClick = {
                        viewModel.updateMetronomeSettings { copy(soundId = sound.id) }
                        viewModel.previewMetronomeSound(sound)
                    },
                    label = { Text(sound.label()) },
                )
            }
        }
    }
    SettingsSubsection(title = stringResource(Res.string.metronome_volume)) {
        val volumeDescription = stringResource(Res.string.metronome_volume)
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Slider(
                modifier = Modifier.weight(1f).semantics { contentDescription = volumeDescription },
                value = settings.volume,
                onValueChange = { value -> viewModel.updateMetronomeSettings { copy(volume = value) } },
            )
            Text(
                modifier = Modifier.padding(start = 16.dp),
                text = "${(settings.volume * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
    SwitchListItem(
        title = stringResource(Res.string.metronome_flash),
        isChecked = settings.isVisualBeatEnabled,
        onCheckedChange = { value -> viewModel.updateMetronomeSettings { copy(isVisualBeatEnabled = value) } },
    )
    if (rememberBeatHaptics() != null) {
        SwitchListItem(
            title = stringResource(Res.string.metronome_vibrate),
            isChecked = settings.isHapticBeatEnabled,
            onCheckedChange = { value -> viewModel.updateMetronomeSettings { copy(isHapticBeatEnabled = value) } },
        )
    }
    SwitchListItem(
        title = stringResource(Res.string.metronome_mute),
        description = stringResource(Res.string.metronome_mute_description),
        isChecked = settings.isMuted,
        onCheckedChange = { value -> viewModel.updateMetronomeSettings { copy(isMuted = value) } },
    )
}

@Composable
private fun MetronomeSound.label() = stringResource(
    when (this) {
        MetronomeSound.CLICK -> Res.string.metronome_sound_click
        MetronomeSound.WOODBLOCK -> Res.string.metronome_sound_woodblock
        MetronomeSound.BEEP -> Res.string.metronome_sound_beep
        MetronomeSound.STICKS -> Res.string.metronome_sound_sticks
        MetronomeSound.COWBELL -> Res.string.metronome_sound_cowbell
    }
)

private val COMMON_TIME_SIGNATURES = listOf(
    TimeSignature(2, 4),
    TimeSignature(3, 4),
    TimeSignature(4, 4),
    TimeSignature(6, 8),
    TimeSignature(7, 8),
    TimeSignature(12, 8),
)
