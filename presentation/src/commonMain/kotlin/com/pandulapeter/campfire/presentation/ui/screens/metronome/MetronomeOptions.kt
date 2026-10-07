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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.metronome.api.model.MetronomePattern
import com.pandulapeter.campfire.metronome.api.model.MetronomeSound
import com.pandulapeter.campfire.metronome.api.model.Subdivision
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.metronome_sound_beep
import com.pandulapeter.campfire.presentation.resources.metronome_sound_click
import com.pandulapeter.campfire.presentation.resources.metronome_sound_cowbell
import com.pandulapeter.campfire.presentation.resources.metronome_sound_hi_hat
import com.pandulapeter.campfire.presentation.resources.metronome_sound_woodblock
import com.pandulapeter.campfire.presentation.resources.metronome_tempo_slider
import com.pandulapeter.campfire.presentation.resources.metronome_volume
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_tempo
import com.pandulapeter.campfire.presentation.ui.components.CHIP_GAP
import com.pandulapeter.campfire.presentation.ui.components.SegmentedChoice
import com.pandulapeter.campfire.presentation.ui.components.SelectableChip
import com.pandulapeter.campfire.presentation.ui.metronome.TempoStepper
import com.pandulapeter.campfire.presentation.ui.metronome.tempoMarking
import kotlin.math.roundToInt

/**
 * How fast the click goes: the song details screen's own tempo pill, Tap segment and all, so that a tempo is set the
 * same way on the tab as in a song, standing next to its name and the Italian marking it falls under the way a setting's
 * control stands next to its label, with a slider across the whole range under them for the long way from one tempo to
 * another.
 */
@Composable
internal fun TempoSetting(
    bpm: Int,
    onBpmChanged: (Int) -> Unit,
) = Column {
    // The pill's number cross-fades for a change made on the pill and is replaced in place for one the slider makes: a
    // drag changes it many times a second, and a fade restarted that often never gets as far as showing a number.
    var pillChanges by remember { mutableIntStateOf(0) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(
                text = stringResource(Res.string.song_editor_insert_tempo),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = tempoMarking(bpm),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TempoStepper(
            bpm = bpm,
            // The tab's tempo is nobody's override of anything, so no value of it is set apart or reset.
            isDefault = true,
            valueKey = pillChanges,
            onStep = { delta ->
                pillChanges++
                onBpmChanged(bpm + delta)
            },
            onTapped = { tapped ->
                pillChanges++
                onBpmChanged(tapped)
            },
            onReset = null,
        )
    }
    val sliderDescription = stringResource(Res.string.metronome_tempo_slider)
    Slider(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).semantics { contentDescription = sliderDescription },
        value = bpm.toFloat(),
        onValueChange = { onBpmChanged(it.roundToInt()) },
        valueRange = MetronomePattern.BPM_RANGE.first.toFloat()..MetronomePattern.BPM_RANGE.last.toFloat(),
    )
}

/**
 * How many clicks each beat is cut into, as a segmented row of the numbers themselves. A number rather than a note
 * value on each segment, since which note a beat is cut into depends on the bar - two clicks to a beat are eighths in
 * 4/4 and sixteenths in 6/8 - and since four of those words do not fit a phone side by side in every language, where
 * four digits always do. The subsection around it says what the numbers count.
 */
@Composable
internal fun SubdivisionChoice(
    selected: Subdivision,
    onSelected: (Subdivision) -> Unit,
) = SegmentedChoice(
    options = Subdivision.entries.map { it to it.clicksPerBeat.toString() },
    selected = selected,
    onSelected = onSelected,
)

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
internal fun VolumeSlider(
    volume: Float,
    onVolumeChanged: (Float) -> Unit,
) = Row(
    modifier = Modifier.padding(horizontal = 16.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    val volumeDescription = stringResource(Res.string.metronome_volume)
    Slider(
        modifier = Modifier.weight(1f).semantics { contentDescription = volumeDescription },
        value = volume,
        onValueChange = onVolumeChanged,
    )
    Text(
        modifier = Modifier.padding(start = 16.dp),
        text = "${(volume * 100).roundToInt()}%",
        style = MaterialTheme.typography.labelLarge,
    )
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
