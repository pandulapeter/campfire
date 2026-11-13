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

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import com.pandulapeter.campfire.chordpro.model.GridToken
import com.pandulapeter.campfire.presentation.ui.songLayout.GridCell
import com.pandulapeter.campfire.presentation.ui.songLayout.alignedGridBars
import com.pandulapeter.campfire.presentation.ui.songLayout.wrappedOpening
import com.pandulapeter.campfire.presentation.ui.theme.LocalSecondAccentColor

/**
 * One `{start_of_grid}` line: bars, chords, beats and repeats laid out as a chord chart, in the columns
 * [alignedGridBars] lined them up in with the other lines of the run. A line wider than its column breaks between bars
 * rather than being cut off, the way a staff of tablature is broken into systems, and the new row starts with a bar line
 * of its own ([wrappedOpening]), as a chart's rows do; a single bar wider than the column wraps inside itself, so every
 * chord of it stays on the page at any text size.
 *
 * Each bar is one text rather than a text per token, since only the characters of one monospaced text are sure to
 * stand in the columns of the line above it: the gaps between them are spaces of the same font. Where a row breaks is
 * only known once the bars are measured, so every bar after the first has its opening measured too, placed only where
 * the bar starts a row.
 */
@Composable
internal fun SongGridLine(
    bars: List<List<GridCell>>,
    lyricsStyle: TextStyle,
    chordStyle: TextStyle,
) {
    val chordColor = LocalSecondAccentColor.current
    val barColor = MaterialTheme.colorScheme.outline
    val beatColor = MaterialTheme.colorScheme.onSurfaceVariant
    val openings = remember(bars) { bars.indices.map { bars.wrappedOpening(it) } }
    Layout(
        modifier = Modifier.fillMaxWidth(),
        content = {
            bars.forEach { bar ->
                Text(
                    text = gridBarText(bar, chordStyle, chordColor = chordColor, barColor = barColor, beatColor = beatColor),
                    style = lyricsStyle,
                )
            }
            // A bar line drawn again where a row breaks says nothing a screen reader has not read already.
            openings.forEach { opening ->
                Text(
                    modifier = Modifier.clearAndSetSemantics {},
                    text = opening?.let { "$it " }.orEmpty(),
                    style = lyricsStyle,
                    color = barColor,
                )
            }
        },
    ) { measurables, constraints ->
        val openingPlaceables = measurables.drop(bars.size).map { it.measure(constraints.copy(minWidth = 0)) }
        // A bar is only ever too wide for a row it starts, which also holds the opening drawn before it. Measured
        // without a bound (how a section's widest line is found), the line is one row as wide as its bars.
        val barPlaceables = measurables.take(bars.size).mapIndexed { index, measurable ->
            val maxWidth = if (constraints.hasBoundedWidth) (constraints.maxWidth - openingPlaceables[index].width).coerceAtLeast(0) else Constraints.Infinity
            measurable.measure(constraints.copy(minWidth = 0, maxWidth = maxWidth))
        }
        val positions = gridRowPositions(
            barWidths = barPlaceables.map { it.width },
            barHeights = barPlaceables.map { it.height },
            openingWidths = openingPlaceables.map { it.width },
            maxWidth = constraints.maxWidth,
        )
        val width = if (constraints.hasBoundedWidth) {
            constraints.maxWidth
        } else {
            positions.bars.indices.maxOfOrNull { positions.bars[it].x + barPlaceables[it].width } ?: 0
        }
        layout(width.coerceAtLeast(constraints.minWidth), positions.height) {
            positions.bars.forEachIndexed { index, bar ->
                if (bar.opensRow && index > 0) openingPlaceables[index].place(bar.x - openingPlaceables[index].width, bar.y)
                barPlaceables[index].place(bar.x, bar.y)
            }
        }
    }
}

/** One bar of a grid line as it is drawn, its chords in [chordStyle]; the colours change nothing of its width. */
internal fun gridBarText(
    bar: List<GridCell>,
    chordStyle: TextStyle,
    chordColor: Color = Color.Unspecified,
    barColor: Color = Color.Unspecified,
    beatColor: Color = Color.Unspecified,
) = buildAnnotatedString {
    bar.forEach { cell ->
        val spanStyle = when (cell.token) {
            is GridToken.Bar -> SpanStyle(color = barColor)
            is GridToken.Chord -> chordStyle.toSpanStyle().copy(color = chordColor)
            GridToken.Beat, is GridToken.Repeat -> SpanStyle(color = beatColor)
            is GridToken.Text, null -> null
        }
        if (spanStyle == null) append(cell.text) else withStyle(spanStyle) { append(cell.text) }
    }
}

/** What a grid token is drawn as: a beat as a raised dot, which reads as a beat where a full stop reads as text. */
internal fun GridToken.displayText() = when (this) {
    is GridToken.Bar -> text
    is GridToken.Chord -> name
    GridToken.Beat -> BEAT_SYMBOL
    is GridToken.Repeat -> text
    is GridToken.Text -> text
}

private const val BEAT_SYMBOL = "\u00B7"
