/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.chords

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.chordpro.model.ChordInstrument
import com.pandulapeter.campfire.chordpro.model.ChordTuning
import com.pandulapeter.campfire.presentation.ui.theme.LocalSecondAccentColor

/**
 * One chord of the Chords tab, and of the scale sheet's chords: its name in the chords' accent color over its diagram,
 * drawn with [selection], the shape the songs draw it with (above the capo of a song's scale, see [SongChord.capo]), on a card that opens all of its shapes. A shape chosen in
 * that sheet crossfades in as the sheet settles on it, as the song details screen's cells do.
 */
@Composable
internal fun ChordLibraryCell(
    modifier: Modifier = Modifier,
    chord: SongChord,
    tuning: ChordTuning,
    selection: SelectedShape,
    onClick: () -> Unit,
) {
    val description = chordCellDescription(
        ChordCell(name = chord.name, letterName = chord.letterName, tuning = tuning, root = chord.chord.root, selection = selection, capo = chord.capo),
    )
    Surface(
        // The description is set here, without clearing, and the content cleared below: a clearing node here would also clear
        // the click action and the button role the clickable Surface adds after this modifier.
        modifier = modifier.semantics { contentDescription = description },
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clearAndSetSemantics { },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                modifier = Modifier.padding(horizontal = 8.dp),
                text = chord.name,
                style = MaterialTheme.typography.titleMedium,
                color = LocalSecondAccentColor.current,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Each shape is drawn on the strings it was found for, so the outgoing one never lands on the next tuning's.
            Crossfade(
                targetState = selection.shape to tuning,
                label = "chordLibraryShape",
            ) { (shape, shapeTuning) ->
                ChordDiagram(
                    modifier = Modifier.size(width = shapeTuning.instrument.diagramWidth, height = shapeTuning.instrument.diagramHeight),
                    geometry = remember(shape, shapeTuning, chord.chord.root, chord.capo) {
                        shape?.let { chordDiagramGeometryOf(it, shapeTuning, chord.chord.root, chord.capo) } ?: emptyChordDiagramGeometryOf(shapeTuning.instrument)
                    },
                    description = description,
                )
            }
        }
    }
}

/** How narrow a column of the Chords tab gets, and how wide the scale sheet's cells are: a diagram and the card around it. */
internal fun chordCellMinWidth(instrument: ChordInstrument?) = (instrument ?: ChordInstrument.GUITAR).diagramWidth + 24.dp

private val ChordInstrument.diagramWidth: Dp get() = if (isFretted) 84.dp else 168.dp

private val ChordInstrument.diagramHeight: Dp get() = if (isFretted) 104.dp else 72.dp
