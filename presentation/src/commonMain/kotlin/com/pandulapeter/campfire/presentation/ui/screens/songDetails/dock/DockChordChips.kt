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

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.ui.chords.ChordCell
import com.pandulapeter.campfire.presentation.ui.components.animatePlacement
import com.pandulapeter.campfire.presentation.ui.theme.LocalSecondAccentColor

/**
 * The song's chords as a chip each in the dock's first row, where the room left to it holds no diagrams: named as the
 * page names them, in the colour its chords are written in, after its gloss where the diagrams would carry one (its
 * degree, or its letters in another notation) in their smaller line's style, beside the name since a chip has room for
 * one line only, in the order the dock puts them ([dockCellOrder]: the
 * screen's first once it has settled, each gliding to its place). A chip opens the chord's sheet as its diagram would
 * ([onChordClicked], null where shapes are not picked here, which leaves it a label).
 */
@Composable
internal fun DockChordChips(
    cells: List<ChordCell>,
    onChordClicked: ((chordName: String) -> Unit)?,
) {
    val placement = Modifier.animatePlacement(MaterialTheme.motionScheme.defaultSpatialSpec())
    cells.forEach { cell ->
        key(cell.name) { DockChordChip(modifier = placement, cell = cell, onChordClicked = onChordClicked) }
    }
}

/** One chip of [DockChordChips]. */
@Composable
private fun DockChordChip(
    modifier: Modifier = Modifier,
    cell: ChordCell,
    onChordClicked: ((chordName: String) -> Unit)?,
) {
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = DOCK_ROW_HEIGHT)
            .clip(FilterChipDefaults.shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, FilterChipDefaults.shape)
            .then(
                if (onChordClicked == null) {
                    Modifier
                } else {
                    Modifier.clickable(role = Role.Button) {
                        println("Dock: chord chip")
                        onChordClicked(cell.name)
                    }
                },
            )
            .padding(horizontal = CHORD_CHIP_PADDING),
        horizontalArrangement = Arrangement.spacedBy(GLOSS_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        (cell.letterName ?: cell.degree)?.let { gloss ->
            Text(
                modifier = Modifier.alignByBaseline(),
                text = gloss,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Text(
            modifier = Modifier.alignByBaseline(),
            text = cell.name,
            style = MaterialTheme.typography.labelLarge,
            color = LocalSecondAccentColor.current,
            maxLines = 1,
        )
    }
}

private val CHORD_CHIP_PADDING = 12.dp
private val GLOSS_GAP = 4.dp
