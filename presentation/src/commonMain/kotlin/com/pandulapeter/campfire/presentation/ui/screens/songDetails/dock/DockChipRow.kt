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

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.ui.songView.model.ChordDiagrams

/**
 * The dock's row of chips: the one row (a short window's, an import preview's, or one without the diagrams: the
 * one-line instrument card, the chord chips and the scale chips scrolling, the chip-high tempo chip at its end), or the
 * scale row alone over the diagram row. It stands on the dock's bottom, or on the diagram row where [parts] has one, and
 * glides between the two on the spring the dock's height moves on, so that it keeps its place on the screen while the
 * diagram row arrives under it or leaves. Of its [parts] only the [visible] ones are drawn.
 */
@Composable
internal fun DockChipRow(
    modifier: Modifier,
    content: DockContent?,
    order: DockRowsOrder,
    tempo: DockTempo?,
    chordDiagrams: ChordDiagrams?,
    metronome: DockMetronome,
    onTunerClicked: (() -> Unit)?,
    parts: Set<DockPart>,
    visible: Set<DockPart>,
    layout: DockLayout,
    horizontalPadding: PaddingValues,
    scrollState: ScrollState,
    isBesidePanel: Boolean,
) {
    val hasDiagramRow = DockPart.DIAGRAM_ROW in parts
    val bottom by animateDpAsState(layout.bottomPadding + if (hasDiagramRow) layout.diagramRowHeight + DOCK_ROW_GAP else 0.dp)
    val alpha by dockPartAlpha(DockPart.CHIP_ROW in visible)
    val contentPart = parts.firstOrNull { it in CHIP_ROW_CONTENTS }
    val contentAlpha by dockPartAlpha(contentPart in visible)
    DockScrollingRow(
        modifier = modifier
            .offset { IntOffset(0, -bottom.roundToPx()) }
            .graphicsLayer { this.alpha = alpha },
        height = layout.rowHeight,
        horizontalPadding = horizontalPadding,
        scrollState = scrollState,
        start = chordDiagrams?.takeIf { DockPart.CHIP_ROW_CARD in parts }?.let {
            @Composable { DockInstrumentPill(chordDiagrams = it, height = layout.rowHeight, isTall = false, isCompact = false, onTunerClicked = onTunerClicked) }
        },
        end = tempo?.takeIf { DockPart.CHIP_ROW_TEMPO in parts }?.let {
            @Composable { DockTempoChip(tempo = it, metronome = metronome, height = layout.rowHeight) }
        },
        isStartShown = DockPart.CHIP_ROW_CARD in visible,
        isEndShown = DockPart.CHIP_ROW_TEMPO in visible,
        fadesAtEnd = isBesidePanel,
        // A one-line card of long names would take the whole row from the chips it is there for.
        minScrollingRoom = if (hasDiagramRow) 0.dp else DOCK_MIN_SCROLLING_ROOM,
        contentModifier = Modifier.graphicsLayer { this.alpha = contentAlpha },
    ) {
        if (content != null) {
            if (contentPart == DockPart.CHIP_ROW_CHORDS_AND_SCALES || contentPart == DockPart.CHIP_ROW_CHORDS) {
                DockChordChips(cells = order.cells, onChordClicked = content.chordDiagrams?.onChordClicked)
            }
            if (contentPart == DockPart.CHIP_ROW_CHORDS_AND_SCALES || contentPart == DockPart.CHIP_ROW_SCALES) {
                DockScaleChips(content = content, order = order)
            }
        }
    }
}

private val CHIP_ROW_CONTENTS = setOf(DockPart.CHIP_ROW_CHORDS_AND_SCALES, DockPart.CHIP_ROW_CHORDS, DockPart.CHIP_ROW_SCALES)
