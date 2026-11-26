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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.stepping.SongStepper
import com.pandulapeter.campfire.presentation.ui.songView.rememberChordCells

/**
 * What scrolls in the side panel for one song's [content], over the tempo chip at its foot: the diagrams at
 * the Chords section's size in a grid, every chord of the song. The scale suggestions are the row under the song beside
 * the panel, where they scroll sideways as under the song elsewhere. The chords on the screen the page's [stepper] shows are
 * drawn as they are and the rest faded back ([rememberChordsOnScreen]); the panel moves only under the reader's finger.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DockPanelRows(
    modifier: Modifier = Modifier,
    content: DockContent?,
    stepper: SongStepper?,
    layout: DockLayout,
) {
    if (content == null) return
    val model = content.model
    val cells = rememberChordCells(model, content.chordDiagrams)
    val names = remember(cells) { cells.mapTo(mutableSetOf()) { it.name } }
    FlowRow(
        // As wide as its widest line, centred as a block over the tempo chip, so that an odd last chord stays in
        // its column rather than being centred on a line of its own.
        modifier = modifier.fillMaxWidth().padding(horizontal = DOCK_PANEL_PADDING).wrapContentWidth(),
        horizontalArrangement = Arrangement.spacedBy(DOCK_ITEM_GAP),
        verticalArrangement = Arrangement.spacedBy(DOCK_ITEM_GAP),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        DockDiagramStrip(
            cells = cells,
            isSmall = false,
            rowHeight = layout.diagramRowHeight,
            markedChords = rememberChordsOnScreen(stepper, model.sections, names),
            onChordClicked = content.chordDiagrams?.onChordClicked,
        )
    }
}
