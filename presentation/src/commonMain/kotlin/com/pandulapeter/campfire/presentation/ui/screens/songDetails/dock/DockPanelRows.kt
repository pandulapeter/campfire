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
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.fillMaxWidth
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
 * The side panel's contents for one song's [content], top to bottom: what starts the dock ([DockStart]) and the tempo
 * chip ([DockTempoChip]), wrapping onto a second line where the two do not fit beside each other, then the scale chips
 * wrapping, each group's title on a line of its own, then the diagrams at the Chords section's size in a grid, every chord
 * of the song. The controls are centred, the panel being as wide as the tempo chip (`dockPanelWidthOf`), which keeps it
 * with the metronome all it holds too: the metronome panel the rows under the song get then has no room here. Rows the
 * switches leave out are left out here too ([rows]). Nothing scrolls sideways: on a page turn the panel
 * ([scrollState]) scrolls only as far as brings the first chord of the page the reader is headed for into view, which
 * the page's [stepper] says, as the strip under the song scrolls to it.
 */
@Composable
internal fun DockPanelRows(
    modifier: Modifier = Modifier,
    content: DockContent?,
    stepper: SongStepper?,
    scrollState: ScrollState,
    metronome: DockMetronome,
    rows: DockRows,
    layout: DockLayout,
) = Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(PANEL_ROW_GAP),
) {
    if (content == null) return@Column
    val cells = rememberChordCells(content.model, content.chordDiagrams)
    val dockKey = remember(content.playingControls, content.model) { dockKeyOf(content.playingControls, content.model) }
    val hasStart = dockKey != null || content.hasInstrumentTools(keyOpensPlaying = dockKey?.opensPlaying == true)
    val tempo = content.tempo
    if (hasStart || tempo != null) {
        PanelFlowRow(isCentered = true) {
            if (hasStart) DockStart(content = content, dockKey = dockKey, height = layout.rowHeight)
            if (tempo != null) DockTempoChip(tempo = tempo, metronome = metronome, height = layout.rowHeight)
        }
    }
    if (rows.hasScaleRow) PanelFlowRow { DockScaleChips(content = content, isNarrow = true) }
    if (rows.hasChordRow) {
        val model = content.model
        val headedSection by remember(stepper) { derivedStateOf { stepper?.headedSection } }
        val names = remember(cells) { cells.mapTo(mutableSetOf()) { it.name } }
        val headedChord = remember(model, names, headedSection) { headedSection?.let { section -> firstChordFrom(model.sections, section, names) } }
        PanelFlowRow {
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

/** One of the side panel's rows, whose items wrap onto as many lines as they need, each line [isCentered] or from the start. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PanelFlowRow(
    isCentered: Boolean = false,
    content: @Composable FlowRowScope.() -> Unit,
) = FlowRow(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = if (isCentered) Arrangement.spacedBy(DOCK_ITEM_GAP, Alignment.CenterHorizontally) else Arrangement.spacedBy(DOCK_ITEM_GAP),
    verticalArrangement = Arrangement.spacedBy(DOCK_ITEM_GAP),
    itemVerticalAlignment = Alignment.CenterVertically,
    content = content,
)

private val PANEL_ROW_GAP = 16.dp
