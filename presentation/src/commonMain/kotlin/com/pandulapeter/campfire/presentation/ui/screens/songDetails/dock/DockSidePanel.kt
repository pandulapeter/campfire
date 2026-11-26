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

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.ui.components.fadingOverNavigationBar
import com.pandulapeter.campfire.presentation.ui.components.fadingVerticalEdges
import com.pandulapeter.campfire.presentation.ui.songView.model.ChordDiagrams

/**
 * The dock as a side panel at the end of the song (this package's `CLAUDE.md`), where a wide window puts it
 * ([isDockSidePanel]): one surface as tall as the song, rounded at its top start corner, as wide as [dock] says, the end
 * inset of [contentPadding] included. It grows in from the screen's end edge and shrinks back into it as the window
 * switches between the panel and the rows under the song, its content laid out at its full width all the while, so that
 * it slides rather than reflows. Its contents are the song's that the pager is on or heading for ([targetPage]),
 * crossfading as a page turns: under an import preview's [card], the tall instrument pill ([DockInstrumentPill]) as wide
 * as the panel and standing still, then the diagrams in a grid ([DockPanelRows]), scrolling where the window is too short
 * for them, and at its foot the tall tempo chip, centred and standing still, the same size as at the end of the rows'
 * diagram row. What scrolls fades out where it meets the instrument pill (or the panel's top) and the tempo chip, or without one at the bottom only over the window's
 * bottom inset, which it scrolls on under and is padded by. A setlist's pager bar ([hasPagerBar]) stands under the panel
 * and the song alike, so the panel then ends at it rather than at the window's edge. Read by screen readers after the
 * app bar and before the song, as the rows under the song are.
 */
@Composable
internal fun DockSidePanel(
    modifier: Modifier = Modifier,
    dock: SongDockState,
    metronome: DockMetronome,
    contentPadding: PaddingValues,
    targetPage: Int,
    hasPagerBar: Boolean,
    card: @Composable () -> Unit,
) {
    // Kept while the panel leaves, so that it shrinks away as it was rather than as nothing.
    val last = remember { LastDockPanel() }
    dock.panelLayout?.let {
        last.layout = it
        last.width = dock.panelTarget
    }
    // Gone once it has shrunk away, so that nothing of it is still read or found beside the song.
    if (dock.panelLayout == null && dock.panelWidth == 0.dp) return
    val layout = last.layout ?: return
    Box(modifier = modifier.fillMaxHeight().width(dock.panelWidth).clipToBounds()) {
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .wrapContentWidth(align = Alignment.End, unbounded = true)
                .width(last.width)
                .fillMaxHeight()
                .clip(RoundedCornerShape(topStart = DOCK_CORNER))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .semantics {
                    isTraversalGroup = true
                    traversalIndex = DOCK_TRAVERSAL_INDEX
                },
        ) {
            val endInset = contentPadding.calculateEndPadding(LocalLayoutDirection.current)
            val bottomInset = if (hasPagerBar) 0.dp else contentPadding.calculateBottomPadding()
            val hasTempo = dock.rows.hasTempo
            Column(modifier = Modifier.fillMaxWidth().padding(end = endInset)) {
                card()
            }
            PanelInstrumentPill(
                modifier = Modifier
                    .padding(end = endInset)
                    .padding(horizontal = DOCK_PANEL_PADDING)
                    .padding(top = DOCK_VERTICAL_PADDING),
                content = dock.contents[targetPage],
                height = layout.tallTempoChipHeight,
                onTunerClicked = dock.onTunerClicked,
            )
            val scrollState = rememberScrollState()
            val background = MaterialTheme.colorScheme.surfaceContainer
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .then(
                        if (hasTempo) {
                            Modifier.fadingVerticalEdges(scrollState = scrollState, backgroundColor = background)
                        } else {
                            Modifier.fadingOverNavigationBar(scrollState = scrollState, backgroundColor = background, bottomInset = bottomInset)
                        },
                    )
                    .verticalScroll(scrollState)
                    .padding(end = endInset, bottom = if (hasTempo) 0.dp else bottomInset),
            ) {
                Crossfade(targetState = targetPage) { page ->
                    DockPanelRows(
                        // As far from the panel's top and the tempo chip as the rows under the song are from their
                        // surface's edge, and as the panel's sides are (DOCK_PANEL_PADDING).
                        modifier = Modifier.padding(vertical = DOCK_VERTICAL_PADDING),
                        content = dock.contents[page],
                        stepper = dock.steppers[page],
                        layout = layout,
                    )
                }
            }
            if (hasTempo) {
                Crossfade(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = endInset, bottom = bottomInset)
                        .padding(horizontal = DOCK_PANEL_PADDING)
                        .padding(bottom = DOCK_VERTICAL_PADDING),
                    targetState = targetPage,
                ) { page ->
                    dock.contents[page]?.tempo?.let { tempo ->
                        // As wide as the instrument pill over the diagrams, so that the panel's controls make one column.
                        DockTempoChip(
                            modifier = Modifier.fillMaxWidth(),
                            tempo = tempo,
                            metronome = metronome,
                            height = layout.tallTempoChipHeight,
                            isTall = true,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The instrument pill at the head of the side panel, as wide as the panel's content. The instrument is the device's, the
 * same on every page, so it stands for the last page that said it while the page turned to has not, rather than
 * crossfading into itself.
 */
@Composable
private fun PanelInstrumentPill(
    modifier: Modifier,
    content: DockContent?,
    height: Dp,
    onTunerClicked: (() -> Unit)?,
) {
    val last = remember { LastChordDiagrams() }
    content?.chordDiagrams?.let { last.value = it }
    last.value?.let { chordDiagrams ->
        DockInstrumentPill(
            modifier = modifier.fillMaxWidth(),
            chordDiagrams = chordDiagrams,
            height = height,
            isTall = true,
            isCompact = false,
            onTunerClicked = onTunerClicked,
        )
    }
}

/** The chord diagrams [PanelInstrumentPill] last drew. Not a state: it is written and read in the same composition. */
private class LastChordDiagrams(var value: ChordDiagrams? = null)

/** The side panel's layout and width as [DockSidePanel] last drew them. Not a state. */
private class LastDockPanel(var layout: DockLayout? = null, var width: Dp = 0.dp)
