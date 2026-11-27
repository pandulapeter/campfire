/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.instrument

import androidx.compose.material3.DividerDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.chordpro.chords.ChordNotation
import com.pandulapeter.campfire.tuner.api.model.InstrumentTuning
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import kotlin.math.roundToInt

/**
 * How wide a one-line [InstrumentPill] is with its names uncut, before it is laid out: its [instrument]'s name and its
 * [tuning]'s, set in [style], each [halfPadding] from its sides, and a control [controlWidth] wide at one of its ends (the
 * room a round end takes on the other), as the pill's halves lay them out.
 */
@Composable
internal fun instrumentPillLineWidth(
    instrument: UserPreferences.Instrument,
    tuning: InstrumentTuning?,
    notation: ChordNotation,
    style: TextStyle,
    halfPadding: Dp,
    controlWidth: Dp,
): Dp {
    val instrumentName = instrumentLabel(instrument)
    val tuningName = shownTuning(instrument, tuning)?.let { tuningName(it, notation) }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(measurer, density, style, instrumentName, tuningName, halfPadding, controlWidth) {
        val widthOf = { text: String -> with(density) { measurer.measure(text = text, style = style, maxLines = 1).size.width.toDp() } }
        val tuningWidth = tuningName?.let { DividerDefaults.Thickness + halfPadding * 2 + widthOf(it) } ?: 0.dp
        controlWidth + INSTRUMENT_PILL_EDGE_PADDING + halfPadding * 2 + widthOf(instrumentName) + tuningWidth
    }
}

/**
 * The widest the tuning half of an [InstrumentPill] may be, out of [available] for both halves, where the instrument half
 * would be [instrumentWidth] wide uncut: whatever the instrument leaves, but never less than [TUNING_SHARE] of the pill,
 * so that a narrow pill cuts the tuning's name short first and then the instrument's rather than leaving the tuning no
 * room at all.
 */
internal fun tuningHalfMaxWidth(available: Int, instrumentWidth: Int) =
    maxOf(available - instrumentWidth, (available * TUNING_SHARE).roundToInt()).coerceIn(0, maxOf(available, 0))

/** The least share of a narrow [InstrumentPill] its tuning half keeps (see [tuningHalfMaxWidth]). */
internal const val TUNING_SHARE = 0.4f

/** Between the pill's rounded ends and the room around the names, inside the halves' presses (see [InstrumentPill]). */
internal val INSTRUMENT_PILL_EDGE_PADDING = 4.dp

/** On either side of each name in an [InstrumentPill]. */
internal val INSTRUMENT_PILL_HALF_PADDING = 12.dp
