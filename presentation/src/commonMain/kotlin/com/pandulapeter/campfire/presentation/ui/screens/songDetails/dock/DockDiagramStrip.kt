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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.song_details_dock_no_chords
import com.pandulapeter.campfire.presentation.ui.chords.ChordCell
import com.pandulapeter.campfire.presentation.ui.chords.ChordDiagram
import com.pandulapeter.campfire.presentation.ui.chords.chordCellDescription
import com.pandulapeter.campfire.presentation.ui.chords.chordDiagramGeometryOf
import com.pandulapeter.campfire.presentation.ui.chords.emptyChordDiagramGeometryOf
import com.pandulapeter.campfire.presentation.ui.theme.LocalSecondAccentColor

/**
 * The song's chords as diagrams in a row of their own, each under its name in the order they are first played, at the
 * Chords section's size or smaller ([isSmall]), the keyboard's wider and lower. A diagram opens the chord's sheet
 * ([onChordClicked], null where shapes are not picked here). A song without chords says so, so that the row, which the
 * screen's switches give the dock rather than the song, is not left looking broken.
 */
@Composable
internal fun DockDiagramStrip(
    cells: List<ChordCell>,
    isSmall: Boolean,
    onChordClicked: ((chordName: String) -> Unit)?,
) {
    if (cells.isEmpty()) {
        Text(
            modifier = Modifier.padding(vertical = 8.dp),
            text = stringResource(Res.string.song_details_dock_no_chords),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    cells.forEach { cell ->
        val geometry = remember(cell) {
            cell.selection.shape?.let { chordDiagramGeometryOf(it, cell.tuning, cell.root, cell.capo) } ?: emptyChordDiagramGeometryOf(cell.tuning.instrument)
        }
        val size = diagramSizeOf(isFretted = cell.tuning.instrument.isFretted, isSmall = isSmall)
        Column(
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .then(if (onChordClicked == null) Modifier else Modifier.clickable(role = Role.Button) { onChordClicked(cell.name) })
                .padding(horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = cell.name,
                style = if (isSmall) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                color = LocalSecondAccentColor.current,
                maxLines = 1,
            )
            ChordDiagram(
                modifier = Modifier.size(size),
                geometry = geometry,
                description = chordCellDescription(cell),
            )
        }
    }
}

/** How large a diagram is drawn: the Chords section's size, or [isSmall] six tenths of it. */
private fun diagramSizeOf(isFretted: Boolean, isSmall: Boolean): DpSize {
    val full = if (isFretted) DpSize(FRETTED_WIDTH, FRETTED_HEIGHT) else DpSize(KEYBOARD_WIDTH, KEYBOARD_HEIGHT)
    return if (isSmall) full * SMALL_SCALE else full
}

private val FRETTED_WIDTH: Dp = 56.dp
private val FRETTED_HEIGHT: Dp = 70.dp
private val KEYBOARD_WIDTH: Dp = 76.dp
private val KEYBOARD_HEIGHT: Dp = 40.dp
private const val SMALL_SCALE = 0.6f
