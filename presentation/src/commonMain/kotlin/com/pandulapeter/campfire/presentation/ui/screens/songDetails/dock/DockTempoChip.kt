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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.metronome.api.model.MetronomePattern
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.song_details_dock_tempo
import com.pandulapeter.campfire.presentation.resources.song_details_tempo
import com.pandulapeter.campfire.presentation.ui.components.controls.StepperDivider
import com.pandulapeter.campfire.presentation.ui.metronome.BeatRow
import com.pandulapeter.campfire.presentation.ui.metronome.MetronomeIcon

/**
 * The tempo chip that ends the dock's row of diagrams (or its one row): the page's tempo ([DockTempo.bpm]) and the bar
 * the click counts as a row of beats lit while it plays (as many as the bar has, a playing stretch's own accents until
 * its bar ends, see [DockMetronome]), on one [DockControlPill] whose every part but play and stop opens the Tempo sheet
 * ([DockTempo.onClick]: the stepper, Tap, and the song's accents, too small to tap here). Read only mode's opens
 * nothing, the click still playing. The number holds the widest tempo's room, so that play and stop never move. The
 * metronome mark stands before it, swinging with the click as the navigation's does ([DockMetronome.iconBeat]), as the
 * tuner's pill has the tuner's: the number alone does not say whose it is.
 *
 * [isTall] is the diagram row's: as tall as the diagrams beside it, the number over the beats and play and stop a round
 * button beside them ([DockRoundPlayStop]). Otherwise it is as tall as a chip, the beats after the number and play and
 * stop after a divider at its end ([DockPlayStop]); [isStretched] where it is made wider than it needs (the side
 * panel's, as wide as the panel's other pills), the beats then centred in what is left. A new number crossfades.
 */
@Composable
internal fun DockTempoChip(
    modifier: Modifier = Modifier,
    tempo: DockTempo,
    metronome: DockMetronome,
    height: Dp,
    isTall: Boolean = false,
    isStretched: Boolean = false,
) = DockControlPill(
    modifier = modifier.height(height),
    shape = if (isTall) RoundedCornerShape(DOCK_TALL_CORNER) else CircleShape,
    onClickLabel = stringResource(Res.string.song_details_dock_tempo),
    onClick = tempo.onClick,
) {
    val panel = metronome.panel
    val beats = @Composable { width: Dp, blockHeight: Dp ->
        BeatRow(
            modifier = Modifier.width(width).clearAndSetSemantics {},
            beatLevels = panel.beatLevels,
            beats = metronome.beats,
            isPlaying = panel.isPlaying,
            isFlashEnabled = panel.isFlashEnabled,
            blockHeight = blockHeight,
            blockGap = BEAT_GAP,
            onBeatLevelsChanged = null,
        )
    }
    if (isTall) {
        Row(
            modifier = Modifier.padding(start = TEMPO_CHIP_PADDING, end = TALL_BUTTON_PADDING),
            horizontalArrangement = Arrangement.spacedBy(TEMPO_CHIP_PADDING),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(TALL_GAP, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(ICON_GAP), verticalAlignment = Alignment.CenterVertically) {
                    TempoMark(metronome = metronome)
                    TempoNumber(bpm = tempo.bpm, style = MaterialTheme.typography.titleMedium)
                }
                beats(TALL_BEATS_WIDTH, TALL_BEAT_HEIGHT)
            }
            DockRoundPlayStop(isPlaying = panel.isPlaying, onClick = metronome.onPlayStop)
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TempoMark(modifier = Modifier.padding(start = TEMPO_CHIP_PADDING, end = ICON_GAP), metronome = metronome)
            TempoNumber(
                modifier = Modifier.padding(end = BEAT_ROW_GAP),
                bpm = tempo.bpm,
                style = MaterialTheme.typography.labelLarge,
            )
            if (isStretched) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) { beats(BEATS_WIDTH, BEAT_HEIGHT) }
            } else {
                beats(BEATS_WIDTH, BEAT_HEIGHT)
            }
            Box(modifier = Modifier.padding(start = BEAT_ROW_GAP)) { StepperDivider() }
            DockPlayStop(isPlaying = panel.isPlaying, onClick = metronome.onPlayStop)
        }
    }
}

/**
 * How wide the chip-high [DockTempoChip] is, whatever the tempo: the metronome mark, the widest tempo at the interface's
 * text size, which a large system text size widens, and the beats, the divider, and play and stop beside it. The side panel is made as wide
 * as this and a little more (`dockPanelWidthOf`), the pill stretched to it.
 */
@Composable
internal fun dockTempoChipWidth(): Dp {
    val style = MaterialTheme.typography.labelLarge
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val numberWidth = remember(measurer, style, density) {
        with(density) { measurer.measure(text = MetronomePattern.BPM_RANGE.last.toString(), style = style, maxLines = 1).size.width.toDp() }
    }
    return TEMPO_CHIP_PADDING + ICON_SIZE + ICON_GAP + numberWidth + BEAT_ROW_GAP + BEATS_WIDTH + BEAT_ROW_GAP + DividerDefaults.Thickness + DOCK_PLAY_STOP_WIDTH
}

/** The metronome mark before the tempo, as large as the tuner pill's mark, swinging with the click. */
@Composable
private fun TempoMark(
    modifier: Modifier = Modifier,
    metronome: DockMetronome,
) = MetronomeIcon(
    modifier = modifier.size(ICON_SIZE),
    beat = metronome.iconBeat,
    contentDescription = null,
    tint = MaterialTheme.colorScheme.onSurfaceVariant,
)

/** The page's tempo in [style], as wide as the widest tempo, so that what comes after it never moves. */
@Composable
private fun TempoNumber(
    modifier: Modifier = Modifier,
    bpm: Int,
    style: TextStyle,
) {
    val description = stringResource(Res.string.song_details_tempo, bpm.toString())
    Box(
        modifier = modifier.semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            modifier = Modifier.alpha(0f).clearAndSetSemantics {},
            text = MetronomePattern.BPM_RANGE.last.toString(),
            style = style,
        )
        AnimatedContent(
            targetState = bpm,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            contentAlignment = Alignment.Center,
        ) { shownBpm ->
            Text(
                modifier = Modifier.clearAndSetSemantics {},
                text = shownBpm.toString(),
                style = style,
                maxLines = 1,
            )
        }
    }
}

private val TEMPO_CHIP_PADDING = 12.dp
private val BEAT_ROW_GAP = 8.dp
private val ICON_SIZE = 18.dp
private val ICON_GAP = 6.dp

/** As wide as four beats read clearly at, a longer bar's narrowing to fit: the chip is as wide whatever the bar. */
private val BEATS_WIDTH = 40.dp
private val BEAT_HEIGHT = 16.dp
private val BEAT_GAP = 2.dp

private val TALL_GAP = 6.dp
private val TALL_BEATS_WIDTH = 48.dp
private val TALL_BEAT_HEIGHT = 12.dp
