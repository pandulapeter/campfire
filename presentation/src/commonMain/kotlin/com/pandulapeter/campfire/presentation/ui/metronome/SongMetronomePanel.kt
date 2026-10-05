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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.metronome.api.model.MetronomePattern
import com.pandulapeter.campfire.metronome.api.model.MetronomePlayback
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_add
import com.pandulapeter.campfire.presentation.resources.ic_subtract
import com.pandulapeter.campfire.presentation.resources.metronome_start
import com.pandulapeter.campfire.presentation.resources.metronome_stop
import com.pandulapeter.campfire.presentation.resources.metronome_tempo_decrease
import com.pandulapeter.campfire.presentation.resources.metronome_tempo_increase
import com.pandulapeter.campfire.presentation.resources.song_details_tempo
import com.pandulapeter.campfire.presentation.resources.song_details_tempo_reset
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.screens.metronome.BeatRow
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.Stepper
import org.jetbrains.compose.resources.painterResource

/**
 * The metronome of the song details screen: a panel inside the app bar, under its title row, that the bar's own button
 * shows and hides ([CampfireViewModel.toggleMetronomePanel]). Here the click is something a song is played to, so the
 * panel holds the least of a metronome that is still one - play and stop, the bar as it is heard, and the tempo with
 * its stepper - and everything else about the click, its sound, its subdivision and its accents, is set on the
 * Metronome tab. It is part of the bar rather than something floating under it, so it stays where it is while the song
 * is read and covers none of it.
 *
 * The stepper writes where the song details screen's own tempo control does, so it is left out where that screen has
 * none: for a song in performance mode or opened from an archived setlist, which keep the number alone.
 */
@Composable
internal fun SongMetronomePanel(
    modifier: Modifier = Modifier,
    viewModel: CampfireViewModel,
    isVisible: Boolean,
    contentPadding: PaddingValues,
) = AnimatedVisibility(
    modifier = modifier,
    visible = isVisible,
    // The bar grows a row rather than something sliding out from behind it.
    enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
    exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
) {
    val playback by viewModel.metronomePlayback.collectAsStateWithLifecycle()
    val isPlaying = playback is MetronomePlayback.Playing
    val settings by viewModel.metronomeSettings.collectAsStateWithLifecycle()
    val tempos by viewModel.tempos.collectAsStateWithLifecycle()
    val songsByFileName by viewModel.songsByFileName.collectAsStateWithLifecycle()
    val setlists by viewModel.setlists.collectAsStateWithLifecycle()
    val isPerformanceModeEnabled by viewModel.isPerformanceModeEnabled.collectAsStateWithLifecycle()
    val context = viewModel.metronomeContext
    val pattern = metronomePatternOf(context = context, settings = settings, songOf = songsByFileName::get, tempos = tempos)
    val songContext = context as? MetronomeContext.Song
    val tempo = songContext?.let {
        effectiveTempo(
            song = songsByFileName[it.songFileName],
            setlistFileName = it.setlistFileName,
            tempos = tempos,
            songFileName = it.songFileName,
        )
    }
    val isTempoEditable = songContext == null ||
            !(isPerformanceModeEnabled || setlists.any { it.fileName == songContext.setlistFileName && it.isArchived })
    val layoutDirection = LocalLayoutDirection.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = contentPadding.calculateStartPadding(layoutDirection) + PANEL_PADDING,
                end = contentPadding.calculateEndPadding(layoutDirection) + PANEL_PADDING,
                bottom = PANEL_PADDING,
            )
            .height(PANEL_HEIGHT),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PANEL_PADDING),
    ) {
        val label = stringResource(if (isPlaying) Res.string.metronome_stop else Res.string.metronome_start)
        FilledIconButton(
            modifier = Modifier.semantics { contentDescription = label },
            onClick = viewModel::toggleMetronome,
        ) {
            PlayStopMark(isPlaying = isPlaying)
        }
        BeatRow(
            modifier = Modifier.weight(1f).padding(horizontal = PANEL_PADDING),
            beatLevels = pattern.beatLevels,
            beats = viewModel.metronomeBeats,
            isPlaying = isPlaying,
            isFlashEnabled = settings.isVisualBeatEnabled,
            blockHeight = PANEL_BEAT_HEIGHT,
            blockGap = PANEL_BEAT_GAP,
            onBeatLevelsChanged = null,
        )
        AnimatedContent(
            targetState = isTempoEditable,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
        ) { isEditable ->
            if (isEditable) {
                Stepper(
                    value = pattern.bpm.toString(),
                    isDefault = tempo?.isDefault ?: (pattern.bpm == MetronomePattern.DEFAULT_BPM),
                    decreaseIcon = painterResource(Res.drawable.ic_subtract),
                    decreaseLabel = stringResource(Res.string.metronome_tempo_decrease),
                    canDecrease = pattern.bpm > MetronomePattern.BPM_RANGE.first,
                    onDecrease = { viewModel.stepMetronomeTempo(-1) },
                    increaseIcon = painterResource(Res.drawable.ic_add),
                    increaseLabel = stringResource(Res.string.metronome_tempo_increase),
                    canIncrease = pattern.bpm < MetronomePattern.BPM_RANGE.last,
                    onIncrease = { viewModel.stepMetronomeTempo(1) },
                    resetLabel = stringResource(Res.string.song_details_tempo_reset),
                    onReset = viewModel::resetMetronomeTempo,
                    repeatsOnHold = true,
                )
            } else {
                val description = stringResource(Res.string.song_details_tempo, pattern.bpm.toString())
                Text(
                    modifier = Modifier.padding(8.dp).semantics { contentDescription = description },
                    text = pattern.bpm.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

private val PANEL_HEIGHT = 48.dp
private val PANEL_PADDING = 12.dp
private val PANEL_BEAT_HEIGHT = 24.dp
private val PANEL_BEAT_GAP = 3.dp
