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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.stepping.SongStepper
import com.pandulapeter.campfire.presentation.ui.songView.rememberChordCells

/**
 * What scrolls in the side panel for one song's [content], over the tempo chip at its foot: the diagrams at
 * the Chords section's size in a grid ([DockPanelGrid]), every chord of the song. The scale suggestions are the row under the song beside
 * the panel, where they scroll sideways as under the song elsewhere. The chords on the screen the page's [stepper] shows are
 * drawn as they are and the rest faded back ([rememberChordsOnScreen]), and once the screen has settled they come first
 * and the panel ([scrollState]) scrolls back to its top ([rememberDockRowsOrder]), never while [isHeld].
 */
@Composable
internal fun DockPanelRows(
    modifier: Modifier = Modifier,
    content: DockContent?,
    stepper: SongStepper?,
    isHeld: () -> Boolean,
    scrollState: ScrollState,
    layout: DockLayout,
) {
    if (content == null) return
    val order = rememberDockRowsOrder(
        content = content,
        stepper = stepper,
        cells = rememberChordCells(content.model, content.chordDiagrams),
        isHeld = isHeld,
    )
    ScrollWithDockOrder(order = order.cells, scrollState = scrollState, target = { 0 })
    DockPanelGrid(
        // As wide as its widest line, centred as a block over the tempo chip.
        modifier = modifier.fillMaxWidth().padding(horizontal = DOCK_PANEL_PADDING).wrapContentWidth(),
        gap = DOCK_ITEM_GAP,
    ) {
        DockDiagramStrip(
            cells = order.cells,
            isSmall = false,
            rowHeight = layout.diagramRowHeight,
            markedChords = order.chordsOnScreen,
            onChordClicked = content.chordDiagrams?.onChordClicked,
        )
    }
}
