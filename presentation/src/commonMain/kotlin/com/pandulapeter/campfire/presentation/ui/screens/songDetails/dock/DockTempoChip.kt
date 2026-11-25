/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails.dock

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.metronome.api.model.MetronomePattern
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_expand
import com.pandulapeter.campfire.presentation.resources.song_details_dock_tempo
import com.pandulapeter.campfire.presentation.resources.song_details_tempo
import com.pandulapeter.campfire.presentation.ui.metronome.BeatRow
import org.jetbrains.compose.resources.painterResource

/**
 * The tempo chip that ends the dock's row of diagrams (or its one row): the page's tempo ([DockTempo.bpm]), the bar the
 * click counts as a row of beats lit while it plays (as many as the bar has, a playing stretch's own accents until its
 * bar ends, see [DockMetronome]), and play and stop ([DockPlayStop]) at its end, where they stand whatever the number,
 * which is as wide as the widest tempo. The number opens the Tempo sheet ([DockTempo.onClick], a chevron saying so):
 * the stepper, Tap, and the song's accents, too small to tap here. Read only mode's opens nothing, the click still
 * playing. A new number crossfades.
 */
@Composable
internal fun DockTempoChip(
    modifier: Modifier = Modifier,
    tempo: DockTempo,
    metronome: DockMetronome,
    height: Dp,
) = Row(
    modifier = modifier
        .height(height)
        .clip(FilterChipDefaults.shape)
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, FilterChipDefaults.shape),
    verticalAlignment = Alignment.CenterVertically,
) {
    val sheetLabel = stringResource(Res.string.song_details_dock_tempo)
    val onClick = tempo.onClick
    val bpmDescription = stringResource(Res.string.song_details_tempo, tempo.bpm.toString())
    Row(
        modifier = Modifier
            .fillMaxHeight()
            .then(if (onClick == null) Modifier else Modifier.clickable(role = Role.Button, onClickLabel = sheetLabel, onClick = onClick))
            .padding(start = TEMPO_CHIP_PADDING, end = if (onClick == null) TEMPO_CHIP_PADDING else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.semantics { contentDescription = bpmDescription },
            contentAlignment = Alignment.CenterEnd,
        ) {
            // The widest tempo holds the room, so that play and stop never move as the number changes.
            Text(
                modifier = Modifier.alpha(0f).clearAndSetSemantics {},
                text = MetronomePattern.BPM_RANGE.last.toString(),
                style = MaterialTheme.typography.labelLarge,
            )
            AnimatedContent(
                targetState = tempo.bpm,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                contentAlignment = Alignment.CenterEnd,
            ) { bpm ->
                Text(
                    modifier = Modifier.clearAndSetSemantics {},
                    text = bpm.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                )
            }
        }
        if (onClick != null) {
            Icon(
                modifier = Modifier.padding(horizontal = CHEVRON_PADDING).size(CHEVRON_SIZE),
                painter = painterResource(Res.drawable.ic_expand),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    val panel = metronome.panel
    BeatRow(
        modifier = Modifier
            .width(BEATS_WIDTH)
            .padding(start = if (onClick == null) 0.dp else CHEVRON_PADDING)
            .clearAndSetSemantics {},
        beatLevels = panel.beatLevels,
        beats = metronome.beats,
        isPlaying = panel.isPlaying,
        isFlashEnabled = panel.isFlashEnabled,
        blockHeight = BEAT_HEIGHT,
        blockGap = BEAT_GAP,
        onBeatLevelsChanged = null,
    )
    DockPlayStop(isPlaying = panel.isPlaying, onClick = metronome.onPlayStop)
}

private val TEMPO_CHIP_PADDING = 12.dp
private val CHEVRON_PADDING = 4.dp
private val CHEVRON_SIZE = 18.dp

/** As wide as four beats read clearly at, a longer bar's narrowing to fit: the chip is as wide whatever the bar. */
private val BEATS_WIDTH = 40.dp
private val BEAT_HEIGHT = 16.dp
private val BEAT_GAP = 2.dp
