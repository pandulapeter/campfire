/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails

import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.pandulapeter.campfire.chordpro.model.GridToken
import com.pandulapeter.campfire.presentation.ui.songLayout.GridCell
import com.pandulapeter.campfire.presentation.ui.songLayout.alignedGridBars
import com.pandulapeter.campfire.presentation.ui.theme.LocalSecondAccentColor

/**
 * One `{start_of_grid}` line: bars, chords, beats and repeats laid out as a chord chart, in the columns
 * [alignedGridBars] lined them up in with the other lines of the run. A line wider than its column breaks between bars
 * rather than being cut off, the way a staff of tablature is broken into systems; a single bar wider than the column
 * wraps inside itself, so every chord of it stays on the page at any text size.
 *
 * Each bar is one text rather than a text per token, since only the characters of one monospaced text are sure to
 * stand in the columns of the line above it: the gaps between them are spaces of the same font.
 */
@Composable
internal fun SongGridLine(
    bars: List<List<GridCell>>,
    lyricsStyle: TextStyle,
    chordStyle: TextStyle,
) = FlowRow(modifier = Modifier.fillMaxWidth()) {
    val chordColor = LocalSecondAccentColor.current
    val barColor = MaterialTheme.colorScheme.outline
    val beatColor = MaterialTheme.colorScheme.onSurfaceVariant
    bars.forEach { bar ->
        Text(
            text = buildAnnotatedString {
                bar.forEach { cell ->
                    val spanStyle = when (cell.token) {
                        is GridToken.Bar -> SpanStyle(color = barColor)
                        is GridToken.Chord -> chordStyle.toSpanStyle().copy(color = chordColor)
                        GridToken.Beat, is GridToken.Repeat -> SpanStyle(color = beatColor)
                        is GridToken.Text, null -> null
                    }
                    if (spanStyle == null) append(cell.text) else withStyle(spanStyle) { append(cell.text) }
                }
            },
            style = lyricsStyle,
        )
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
