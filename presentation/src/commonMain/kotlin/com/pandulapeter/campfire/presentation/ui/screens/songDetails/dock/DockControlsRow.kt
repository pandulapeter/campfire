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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.ui.songView.model.ChordDiagrams

/**
 * The dock's controls on a row of their own under the scale row, which was too wide for the room between them: the
 * one-line instrument card at its start and the chip-high tempo chip at its end, as they stand in the one row, with
 * nothing between them. It stands on the dock's bottom. Of its [parts] only the [visible] ones are drawn, each fading
 * and growing in from its end ([DockRowEnd]); the row itself is no more than a place for them.
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
) = DockEndsLayout(
    modifier = modifier
        .padding(bottom = layout.bottomPadding)
        .padding(horizontalPadding)
        .fillMaxWidth()
        .height(layout.rowHeight),
    minMiddleRoom = 0.dp,
    start = {
        if (chordDiagrams != null && DockPart.CONTROLS_ROW_CARD in parts) {
            DockRowEnd(isShown = DockPart.CONTROLS_ROW_CARD in visible, expandFrom = Alignment.Start) {
                DockLineCard(chordDiagrams = chordDiagrams, height = layout.rowHeight, onTunerClicked = onTunerClicked)
            }
        }
    },
    middle = {},
    end = {
        if (tempo != null && DockPart.CONTROLS_ROW_TEMPO in parts) {
            DockRowEnd(isShown = DockPart.CONTROLS_ROW_TEMPO in visible, expandFrom = Alignment.End) {
                DockTempoChip(tempo = tempo, metronome = metronome, height = layout.rowHeight)
            }
        }
    },
)
