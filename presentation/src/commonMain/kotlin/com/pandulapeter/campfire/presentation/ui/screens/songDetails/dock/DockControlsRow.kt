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

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.pandulapeter.campfire.presentation.ui.songView.model.ChordDiagrams

/**
 * The dock's controls on a row of their own under the scale row, which was too wide for the room between them: the
 * one-line instrument card at its start and the chip-high tempo chip at its end, as they stand in the one row, with
 * nothing between them. It stands on the dock's bottom. Of its [parts] only the [visible] ones are drawn.
 */
@Composable
internal fun DockControlsRow(
    modifier: Modifier,
    tempo: DockTempo?,
    chordDiagrams: ChordDiagrams?,
    metronome: DockMetronome,
    onTunerClicked: (() -> Unit)?,
    parts: Set<DockPart>,
    visible: Set<DockPart>,
    layout: DockLayout,
    horizontalPadding: PaddingValues,
) {
    val alpha by dockPartAlpha(DockPart.CONTROLS_ROW in visible)
    DockScrollingRow(
        modifier = modifier
            .padding(bottom = layout.bottomPadding)
            .graphicsLayer { this.alpha = alpha },
        height = layout.rowHeight,
        horizontalPadding = horizontalPadding,
        scrollState = rememberScrollState(),
        start = chordDiagrams?.takeIf { DockPart.CONTROLS_ROW_CARD in parts }?.let {
            @Composable { DockLineCard(chordDiagrams = it, height = layout.rowHeight, onTunerClicked = onTunerClicked) }
        },
        end = tempo?.takeIf { DockPart.CONTROLS_ROW_TEMPO in parts }?.let {
            @Composable { DockTempoChip(tempo = it, metronome = metronome, height = layout.rowHeight) }
        },
        isStartShown = DockPart.CONTROLS_ROW_CARD in visible,
        isEndShown = DockPart.CONTROLS_ROW_TEMPO in visible,
    ) {}
}
