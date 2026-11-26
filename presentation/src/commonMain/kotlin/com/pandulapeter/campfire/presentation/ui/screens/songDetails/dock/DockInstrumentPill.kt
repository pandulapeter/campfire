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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.ui.components.controls.StepperDivider
import com.pandulapeter.campfire.presentation.ui.instrument.InstrumentPill
import com.pandulapeter.campfire.presentation.ui.songView.model.ChordDiagrams

/**
 * The instrument card at the start of the dock's chords: the app's [InstrumentPill], naming the instrument and tuning the
 * diagrams are drawn for, framed as the dock's other controls are (the tempo chip's fill, outline, and [height]), and
 * ending in the tuner ([onTunerClicked], null where the Dock's Tuner switch or the Tuner feature leaves it to the song's
 * menu), built as the tempo chip is so that the row reads tune, the diagrams, play. Stacked, the tuning under the
 * instrument and the tuner a round button beside them ([DockTunerButton]), beside the diagrams ([isTall]: their row, the
 * side panel's head), smaller in a narrow window ([isCompact]) as the tempo chip is; else one line, the tuner after a
 * divider (a short window's one row, and an instrument without shapes, whose card has no diagrams beside it). Read only
 * mode's opens its sheets too, the instrument being the device's rather than the song's.
 */
@Composable
internal fun DockInstrumentPill(
    modifier: Modifier = Modifier,
    chordDiagrams: ChordDiagrams,
    height: Dp,
    isTall: Boolean,
    isCompact: Boolean,
    onTunerClicked: (() -> Unit)?,
) {
    val onInstrumentClicked = chordDiagrams.onInstrumentClicked ?: return
    val compactness = if (isTall) dockCompactness(isCompact) else 0f
    // Kept while the tuner leaves (the Dock's Tuner switch turned off with the song behind the Preferences), so that it
    // shrinks away as it was.
    val lastTunerClick = remember { LastTunerClick() }
    onTunerClicked?.let { lastTunerClick.value = it }
    val typography = MaterialTheme.typography
    InstrumentPill(
        modifier = modifier,
        instrument = chordDiagrams.instrument,
        tuning = chordDiagrams.instrumentTuning,
        notation = chordDiagrams.notation,
        style = dockTallTextStyle(typography.labelLarge, typography.labelMedium, compactness),
        height = height,
        isStacked = isTall,
        shape = if (isTall) RoundedCornerShape(DOCK_TALL_CORNER) else CircleShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        stackedHalfPadding = dockTallChipPadding(compactness),
        trailing = {
            AnimatedVisibility(
                visible = onTunerClicked != null,
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally(),
            ) {
                val onClick = { lastTunerClick.value?.invoke() ?: Unit }
                if (isTall) {
                    // As far from the card's end as the tempo chip's play and stop is from its own.
                    DockTunerButton(
                        modifier = Modifier.padding(end = dockTallButtonPadding(compactness)),
                        roundSize = dockRoundButtonSize(compactness),
                        onClick = onClick,
                    )
                } else {
                    Row(modifier = Modifier.height(height), verticalAlignment = Alignment.CenterVertically) {
                        StepperDivider()
                        DockTunerButton(roundSize = null, onClick = onClick)
                    }
                }
            }
        },
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

/** The tuner's click [DockInstrumentPill] last had. Not a state: it is written and read in the same composition. */
private class LastTunerClick(var value: (() -> Unit)? = null)
