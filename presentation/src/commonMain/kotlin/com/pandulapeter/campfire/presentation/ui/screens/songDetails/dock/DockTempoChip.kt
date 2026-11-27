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
import androidx.compose.ui.unit.lerp
import com.pandulapeter.campfire.metronome.api.model.MetronomePattern
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.song_details_dock_open_tempo
import com.pandulapeter.campfire.presentation.resources.song_details_tempo
import com.pandulapeter.campfire.presentation.ui.components.controls.StepperDivider
import com.pandulapeter.campfire.presentation.ui.metronome.BeatRow
import com.pandulapeter.campfire.presentation.ui.metronome.MetronomeIcon

/**
 * The tempo chip in the dock's bottom end corner, ending its row of diagrams (or its one row, or the side panel's foot):
 * the page's tempo ([DockTempo.bpm]) and the bar
 * the click counts as a row of beats lit while it plays (as many as the bar has, a playing stretch's own accents until
 * its bar ends, see [DockMetronome]), on one [DockControlPill] whose every part but play and stop opens the Tempo sheet
 * ([DockTempo.onClick]: the stepper, Tap, and the song's accents, too small to tap here). Read only mode's opens
 * nothing, the click still playing. The number holds the widest tempo's room, so that play and stop never move. The
 * metronome mark stands before it, swinging with the click as the navigation's does ([DockMetronome.iconBeat]): the
 * number alone does not say whose it is.
 *
 * [isTall] beside diagrams (the diagram row's, and the side panel's): as tall as a row of them, the number over the beats
 * and play and stop a round button beside them ([DockRoundPlayStop]), one size in both (`DockLayout.tallTempoChipHeight`),
 * its text a size smaller and its button and padding smaller in a narrow window ([isCompact]), as the instrument card is.
 * Otherwise it is as tall as a chip, the beats after the number and play and stop after a divider at its end ([DockPlayStop]). A new
 * number crossfades.
 */
@Composable
internal fun DockTempoChip(
    modifier: Modifier = Modifier,
    tempo: DockTempo,
    metronome: DockMetronome,
    height: Dp,
    isTall: Boolean = false,
    isCompact: Boolean = false,
) = DockControlPill(
    modifier = modifier.height(height),
    shape = if (isTall) RoundedCornerShape(DOCK_TALL_CORNER) else CircleShape,
    onClickLabel = stringResource(Res.string.song_details_dock_open_tempo),
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
        val compactness = dockCompactness(isCompact)
        val padding = dockTallChipPadding(compactness)
        Row(
            modifier = Modifier.padding(start = padding, end = dockTallButtonPadding(compactness)),
            // Centred in a chip stretched wider than it (the side panel's, as wide as its instrument pill).
            horizontalArrangement = Arrangement.spacedBy(padding, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(TALL_GAP, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(lerp(ICON_GAP, COMPACT_ICON_GAP, compactness)), verticalAlignment = Alignment.CenterVertically) {
                    TempoMark(metronome = metronome, size = lerp(ICON_SIZE, COMPACT_ICON_SIZE, compactness))
                    val typography = MaterialTheme.typography
                    TempoNumber(bpm = tempo.bpm, style = dockTallTextStyle(typography.titleMedium, typography.titleSmall, compactness))
                }
                beats(lerp(TALL_BEATS_WIDTH, BEATS_WIDTH, compactness), TALL_BEAT_HEIGHT)
            }
            DockRoundPlayStop(isPlaying = panel.isPlaying, size = dockRoundButtonSize(compactness), onClick = metronome.onPlayStop)
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TempoMark(modifier = Modifier.padding(start = DOCK_CHIP_PADDING, end = ICON_GAP), metronome = metronome, size = ICON_SIZE)
            TempoNumber(
                modifier = Modifier.padding(end = BEAT_ROW_GAP),
                bpm = tempo.bpm,
                style = MaterialTheme.typography.labelLarge,
            )
            beats(BEATS_WIDTH, BEAT_HEIGHT)
            Box(modifier = Modifier.padding(start = BEAT_ROW_GAP)) { StepperDivider() }
            DockPlayStop(isPlaying = panel.isPlaying, onClick = metronome.onPlayStop)
        }
    }
}

/**
 * How wide the tall [DockTempoChip] is, whatever the tempo: the metronome mark and the widest tempo at the interface's
 * text size, which a large system text size widens, over the beats, and the round play and stop beside them. The side
 * panel is at least as wide as this (`dockPanelWidthOf`), the chip standing at its foot.
 */
@Composable
internal fun dockTallTempoChipWidth(): Dp = DOCK_CHIP_PADDING + maxOf(ICON_SIZE + ICON_GAP + widestTempoWidth(MaterialTheme.typography.titleMedium), TALL_BEATS_WIDTH) + DOCK_CHIP_PADDING + dockRoundButtonSize(compactness = 0f) +
        dockTallButtonPadding(compactness = 0f)

/**
 * How wide the chip-high [DockTempoChip] is, whatever the tempo: the metronome mark, the widest tempo at the interface's
 * text size, the beats, and play and stop after a divider. The dock weighs it against the room its row leaves
 * (`dockLayoutOf`'s `scalesFitBesideControls` and `controlsFitPagerBar`).
 */
@Composable
internal fun dockTempoChipWidth(): Dp = DOCK_CHIP_PADDING + ICON_SIZE + ICON_GAP + widestTempoWidth(MaterialTheme.typography.labelLarge) + BEAT_ROW_GAP + BEATS_WIDTH + BEAT_ROW_GAP + DividerDefaults.Thickness + DOCK_END_BUTTON_WIDTH

/** How wide the widest tempo is set in [style], the room [TempoNumber] holds whatever the tempo. */
@Composable
private fun widestTempoWidth(style: TextStyle): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(measurer, style, density) {
        with(density) { measurer.measure(text = MetronomePattern.BPM_RANGE.last.toString(), style = style, maxLines = 1).size.width.toDp() }
    }
}

/** The metronome mark before the tempo, swinging with the click. */
@Composable
private fun TempoMark(
    modifier: Modifier = Modifier,
    metronome: DockMetronome,
    size: Dp,
) = MetronomeIcon(
    modifier = modifier.size(size),
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

private val BEAT_ROW_GAP = 8.dp
private val ICON_SIZE = 18.dp
private val ICON_GAP = 6.dp
private val COMPACT_ICON_SIZE = 16.dp
private val COMPACT_ICON_GAP = 4.dp

/** As wide as four beats read clearly at, a longer bar's narrowing to fit: the chip is as wide whatever the bar. */
private val BEATS_WIDTH = 40.dp
private val BEAT_HEIGHT = 16.dp
private val BEAT_GAP = 2.dp

/** The corners of the tall controls beside the diagrams (the tempo chip, the instrument pill), which a pill's would make round. */
internal val DOCK_TALL_CORNER = 16.dp

private val TALL_GAP = 6.dp
private val TALL_BEATS_WIDTH = 48.dp
private val TALL_BEAT_HEIGHT = 12.dp
