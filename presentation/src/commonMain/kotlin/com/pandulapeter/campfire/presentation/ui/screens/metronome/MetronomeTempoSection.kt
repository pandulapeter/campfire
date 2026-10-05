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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.pandulapeter.campfire.metronome.api.model.MetronomeAudioIssue
import com.pandulapeter.campfire.metronome.api.model.MetronomePattern
import com.pandulapeter.campfire.metronome.api.model.MetronomePlayback
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.metronome_accents
import com.pandulapeter.campfire.presentation.resources.metronome_accents_hint
import com.pandulapeter.campfire.presentation.resources.metronome_audio_unavailable
import com.pandulapeter.campfire.presentation.resources.metronome_audio_waiting
import com.pandulapeter.campfire.presentation.resources.metronome_tempo_slider
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.metronome.TapTempoButton
import com.pandulapeter.campfire.presentation.ui.metronome.beatLevelsOf
import com.pandulapeter.campfire.presentation.ui.metronome.tempoMarking
import com.pandulapeter.campfire.presentation.ui.metronome.timeSignatureOrDefault
import com.pandulapeter.campfire.presentation.ui.metronome.withBeatLevels
import com.pandulapeter.campfire.presentation.ui.screens.settings.AnimatedSettingsRow
import com.pandulapeter.campfire.presentation.ui.screens.settings.SettingsMessage
import com.pandulapeter.campfire.presentation.ui.screens.settings.SettingsSection
import com.pandulapeter.campfire.presentation.ui.screens.settings.SettingsSubsection
import kotlin.math.roundToInt

/**
 * What the tab adds to the tempo the metronome panel shows and steps: the marking it falls under, tap tempo and a slider
 * across the whole range; then the beat row, which is where the accents are drawn, and the one line that says why
 * nothing can be heard where that is so.
 */
@Composable
internal fun MetronomeTempoSection(
    modifier: Modifier = Modifier,
    viewModel: CampfireViewModel,
    playback: MetronomePlayback,
) = SettingsSection(modifier = modifier) {
    val settings by viewModel.metronomeSettings.collectAsStateWithLifecycle()
    val bpm = settings.bpm
    val setBpm = { value: Int -> viewModel.updateMetronomeSettings { copy(bpm = MetronomePattern.coerceBpm(value)) } }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = tempoMarking(bpm),
                style = MaterialTheme.typography.titleMedium,
            )
            TapTempoButton(onTempo = setBpm)
        }
        val sliderDescription = stringResource(Res.string.metronome_tempo_slider)
        Slider(
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = sliderDescription },
            value = bpm.toFloat(),
            onValueChange = { setBpm(it.roundToInt()) },
            valueRange = MetronomePattern.BPM_RANGE.first.toFloat()..MetronomePattern.BPM_RANGE.last.toFloat(),
        )
    }
    val timeSignature = settings.timeSignatureOrDefault
    SettingsSubsection(
        title = stringResource(Res.string.metronome_accents),
        description = stringResource(Res.string.metronome_accents_hint),
    ) {
        BeatRow(
            modifier = Modifier.padding(horizontal = 16.dp),
            beatLevels = settings.beatLevelsOf(timeSignature),
            beats = viewModel.metronomeBeats,
            isPlaying = playback is MetronomePlayback.Playing,
            isFlashEnabled = settings.isVisualBeatEnabled,
            onBeatLevelsChanged = { levels -> viewModel.updateMetronomeSettings { withBeatLevels(timeSignature, levels) } },
        )
    }
    AnimatedSettingsRow(value = (playback as? MetronomePlayback.Playing)?.audioIssue) { issue ->
        SettingsMessage(
            text = stringResource(
                when (issue) {
                    MetronomeAudioIssue.UNAVAILABLE -> Res.string.metronome_audio_unavailable
                    MetronomeAudioIssue.WAITING_FOR_GESTURE -> Res.string.metronome_audio_waiting
                }
            ),
        )
    }
}
