/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs.scales

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.chordpro.model.ChordTuning
import com.pandulapeter.campfire.presentation.ui.chords.ChordLibraryCell
import com.pandulapeter.campfire.presentation.ui.chords.SongChord
import com.pandulapeter.campfire.presentation.ui.chords.chordCellMinWidth
import com.pandulapeter.campfire.presentation.ui.chords.selectShape
import com.pandulapeter.campfire.presentation.ui.components.fadingHorizontalEdges

/**
 * One row of a scale's chords under [title]: a cell per degree that stacks into a chord, its numeral over it, drawn with
 * the shape the songs draw it with, scrolling sideways where the sheet is narrower than seven cells, so that the triads
 * and the sevenths each stay one row the eye can run along.
 */
@Composable
internal fun ScaleChordsRow(
    modifier: Modifier = Modifier,
    title: String,
    chords: List<ScaleSheetChord>,
    tuning: ChordTuning,
    storedShapes: Map<String, String>,
    onChordClicked: (SongChord) -> Unit,
) = Column(
    modifier = modifier.fillMaxWidth(),
) {
    Text(
        modifier = Modifier.padding(horizontal = 16.dp).semantics { heading() },
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val scrollState = rememberScrollState()
    Row(
        modifier = Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .fadingHorizontalEdges(scrollState)
            .horizontalScroll(scrollState)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        chords.forEach { (numeral, chord) ->
            Column(
                modifier = Modifier.width(chordCellMinWidth(tuning.instrument)),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    modifier = Modifier.padding(bottom = 4.dp),
                    text = numeral,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ChordLibraryCell(
                    chord = chord,
                    tuning = tuning,
                    selection = remember(chord, tuning, storedShapes) { selectShape(chord, tuning, storedShapes) },
                    onClick = { onChordClicked(chord) },
                )
            }
        }
    }
}
