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

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.ui.chords.hasTooManySongChords
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.stepping.SongStepper
import com.pandulapeter.campfire.presentation.ui.songView.rememberChordCells

/**
 * What scrolls in the side panel for one song's [content], under its controls ([DockPanelControls]): the scale chips
 * as a column, a chip a line under each group's title ([DockScaleChips]), then the diagrams at the Chords section's
 * size in a grid, every chord of the song. Rows the switches leave out are left out here too ([rows]). On a page turn
 * the panel ([scrollState]) scrolls only as far as brings the first chord of the page the reader is headed for into
 * view, which the page's [stepper] says, as the strip under the song scrolls to it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DockPanelRows(
    modifier: Modifier = Modifier,
    content: DockContent?,
    stepper: SongStepper?,
    scrollState: ScrollState,
    rows: DockRows,
    layout: DockLayout,
) = Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(PANEL_ROW_GAP),
) {
    if (content == null) return@Column
    val model = content.model
    val headedSection by remember(stepper) { derivedStateOf { stepper?.headedSection } }
    if (rows.hasScaleRow) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = DOCK_PANEL_PADDING),
            verticalArrangement = Arrangement.spacedBy(DOCK_ITEM_GAP),
        ) {
            DockScaleChips(content = content, isColumn = true)
        }
    }
    if (rows.hasChordRow) {
        val cells = rememberChordCells(model, content.chordDiagrams)
        val names = remember(cells) { cells.mapTo(mutableSetOf()) { it.name } }
        val headedChord = remember(model, names, headedSection) { headedSection?.let { section -> firstChordFrom(model.sections, section, names) } }
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = DOCK_PANEL_PADDING),
            horizontalArrangement = Arrangement.spacedBy(DOCK_ITEM_GAP),
            verticalArrangement = Arrangement.spacedBy(DOCK_ITEM_GAP),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            DockDiagramStrip(
                cells = cells,
                isSmall = false,
                rowHeight = layout.diagramRowHeight,
                hasTooManyChords = remember(model) { cells.isEmpty() && model.shouldShowChords && hasTooManySongChords(model.song, model.notation) },
                scrollState = scrollState,
                headedChord = headedChord,
                markedChords = rememberChordsOnScreen(stepper, model.sections, names),
                onChordClicked = content.chordDiagrams?.onChordClicked,
                isGrid = true,
            )
        }
    }
}

private val PANEL_ROW_GAP = 16.dp
