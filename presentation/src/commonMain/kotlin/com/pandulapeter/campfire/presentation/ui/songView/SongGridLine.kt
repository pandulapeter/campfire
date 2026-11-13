/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songView

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.pandulapeter.campfire.presentation.ui.songLayout.GridBarLayout
import com.pandulapeter.campfire.presentation.ui.songLayout.GridLinePlacement
import com.pandulapeter.campfire.presentation.ui.theme.LocalSecondAccentColor
import kotlin.math.ceil

/**
 * One `{start_of_grid}` line drawn as a row of bars ([drawGridLine]), placed by the run's [layout] at the width it is
 * given: its bars stand in the run's columns, and a line too wide for the width breaks after the same bar as every other
 * line of the run. Measured without a bound (how a section's widest line is found), it is one row as wide as its bars.
 *
 * Where a width places the line is only known once it is measured, so the placement is kept from the measurement for the
 * drawing that follows it; it is not state, since a new placement always comes with a new measurement.
 */
@Composable
internal fun SongGridLine(
    layout: GridBarLayout,
    lineIndex: Int,
    styles: RunStyles,
    textMeasurements: SongTextMeasurements,
) {
    val secondAccent = LocalSecondAccentColor.current
    val scheme = MaterialTheme.colorScheme
    val colors = remember(scheme, secondAccent) {
        GridColors(
            box = scheme.outlineVariant,
            fill = scheme.surfaceContainerLow,
            divider = scheme.surfaceContainerHighest,
            chord = secondAccent,
            hold = secondAccent.copy(alpha = HOLD_ALPHA),
            sign = scheme.outline,
            text = scheme.onSurfaceVariant,
        )
    }
    val texts = remember(styles, textMeasurements) {
        val voltaStyle = styles.text.copy(fontSize = styles.text.fontSize * VOLTA_TEXT_SCALE)
        GridTextLayouts(
            chord = { textMeasurements.gridText(it, styles.chord) },
            text = { textMeasurements.gridText(it, styles.text) },
            volta = { textMeasurements.gridText(it, voltaStyle) },
        )
    }
    val words = rememberGridLineWords()
    val description = remember(layout, lineIndex, words) { gridLineDescription(layout.lines[lineIndex], words) }
    val placement = remember(layout, lineIndex) { arrayOfNulls<GridLinePlacement>(1) }
    Layout(
        modifier = Modifier
            .semantics { contentDescription = description }
            .drawBehind { placement[0]?.let { drawGridLine(it, layout.metrics, texts, colors) } },
        content = {},
    ) { _, constraints ->
        val placed = layout.placeLine(lineIndex, if (constraints.hasBoundedWidth) constraints.maxWidth.toFloat() else Float.POSITIVE_INFINITY)
        placement[0] = placed
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else ceil(placed.width).toInt()
        layout(width.coerceAtLeast(constraints.minWidth), ceil(placed.height).toInt().coerceAtLeast(constraints.minHeight)) {}
    }
}

private const val HOLD_ALPHA = 0.35f
private const val VOLTA_TEXT_SCALE = 0.6f
