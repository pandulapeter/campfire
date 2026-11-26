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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.ui.instrument.InstrumentPill
import com.pandulapeter.campfire.presentation.ui.songView.model.ChordDiagrams

/**
 * The app's [InstrumentPill] at the start of the dock's chords, naming the instrument and tuning the diagrams are drawn
 * for, framed as the dock's other controls are (the tempo chip's fill, outline, and [height]): stacked, the tuning under
 * the instrument, beside the diagrams ([isTall]: their row, the side panel's head), else one line (a short window's one
 * row). Read only mode's opens its sheets too, the instrument being the device's rather than the song's.
 */
@Composable
internal fun DockInstrumentPill(
    modifier: Modifier = Modifier,
    chordDiagrams: ChordDiagrams,
    height: Dp,
    isTall: Boolean,
) {
    val onInstrumentClicked = chordDiagrams.onInstrumentClicked ?: return
    InstrumentPill(
        modifier = modifier,
        instrument = chordDiagrams.instrument,
        tuning = chordDiagrams.instrumentTuning,
        notation = chordDiagrams.notation,
        style = MaterialTheme.typography.labelLarge,
        height = height,
        isStacked = isTall,
        shape = if (isTall) RoundedCornerShape(DOCK_TALL_CORNER) else CircleShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        onInstrumentClicked = {
            println("Dock: instrument pill")
            onInstrumentClicked()
        },
        onTuningClicked = {
            println("Dock: instrument pill tuning")
            chordDiagrams.onTuningClicked?.invoke()
        },
    )
}
