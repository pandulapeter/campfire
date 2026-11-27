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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.pandulapeter.campfire.presentation.ui.songView.model.ChordDiagrams

/**
 * The dock's row of diagrams, on its bottom: the tall instrument pill, the diagrams scrolling, and the tall tempo chip,
 * the two standing still. Of its [parts] only the [visible] ones are drawn: the pill and the chip fade and grow in and
 * out at the row's ends, and the diagrams, whose size ([DockPart.FULL_DIAGRAMS] or [DockPart.SMALL_DIAGRAMS]) is a part
 * of its own, fade out at one size and in at the other rather than jumping between them.
 */
@Composable
internal fun DockDiagramRow(
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
) {
    val alpha by dockPartAlpha(DockPart.DIAGRAM_ROW in visible)
    val sizePart = if (DockPart.SMALL_DIAGRAMS in parts) DockPart.SMALL_DIAGRAMS else DockPart.FULL_DIAGRAMS
    val diagramsAlpha by dockPartAlpha(sizePart in visible)
    DockScrollingRow(
        modifier = modifier
            .padding(bottom = layout.bottomPadding)
            .graphicsLayer { this.alpha = alpha },
        height = layout.diagramRowHeight,
        horizontalPadding = horizontalPadding,
        scrollState = scrollState,
        start = chordDiagrams?.takeIf { DockPart.DIAGRAM_ROW_CARD in parts }?.let {
            @Composable {
                DockInstrumentPill(
                    chordDiagrams = it,
                    height = layout.tallTempoChipHeight,
                    isTall = true,
                    isCompact = layout.areControlsCompact,
                    isTunerAtStart = true,
                    onTunerClicked = onTunerClicked,
                )
            }
        },
        end = tempo?.takeIf { DockPart.DIAGRAM_ROW_TEMPO in parts }?.let {
            @Composable {
                DockTempoChip(tempo = it, metronome = metronome, height = layout.tallTempoChipHeight, isTall = true, isCompact = layout.areControlsCompact)
            }
        },
        isStartShown = DockPart.DIAGRAM_ROW_CARD in visible,
        isEndShown = DockPart.DIAGRAM_ROW_TEMPO in visible,
        contentModifier = Modifier.graphicsLayer { this.alpha = diagramsAlpha },
    ) {
        if (content != null) {
            DockDiagramStrip(
                cells = order.cells,
                isSmall = sizePart == DockPart.SMALL_DIAGRAMS,
                rowHeight = layout.diagramRowHeight,
                markedChords = order.chordsOnScreen,
                onChordClicked = content.chordDiagrams?.onChordClicked,
            )
        }
    }
}
