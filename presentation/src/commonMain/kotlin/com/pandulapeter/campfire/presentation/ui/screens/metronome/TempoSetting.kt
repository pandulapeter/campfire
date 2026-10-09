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

import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.metronome.api.model.MetronomePattern
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.metronome_tempo_slider
import com.pandulapeter.campfire.presentation.resources.song_details_tempo
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_tempo
import com.pandulapeter.campfire.presentation.ui.components.LabeledControlRow
import com.pandulapeter.campfire.presentation.ui.metronome.TEMPO_MARKING_NAMES
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
    val name = stringResource(Res.string.song_editor_insert_tempo)
    LabeledControlRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        label = { TempoLabel(name = name, markings = listOf(tempoMarking(bpm))) },
        // Every marking rather than the one shown, which changes many times a second under a dragged slider: the row
        // takes the form the widest of them needs, so it never changes form, and moves the slider, while it is dragged.
        decidingLabel = { TempoLabel(name = name, markings = TEMPO_MARKING_NAMES) },
    ) {
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
    val sliderState = stringResource(Res.string.song_details_tempo, bpm.toString())
    Slider(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .semantics {
                contentDescription = sliderDescription
                // The range info alone is read as a percentage of the range, which says nothing about a tempo.
                stateDescription = sliderState
            },
        value = bpm.toFloat(),
        onValueChange = { onBpmChanged(it.roundToInt()) },
        valueRange = MetronomePattern.BPM_RANGE.first.toFloat()..MetronomePattern.BPM_RANGE.last.toFloat(),
    )
}

/** The tempo's name over the Italian marking it falls under, each in the style it is drawn in. */
@Composable
private fun TempoLabel(
    name: String,
    markings: List<String>,
) = Column {
    Text(
        text = name,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
    markings.forEach { marking ->
        Text(
            text = marking,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
