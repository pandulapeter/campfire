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

import com.pandulapeter.campfire.presentation.ui.songView.rememberChordCells
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * What left the song for the screen to hold, under the pages and outside the pager (this package's `CLAUDE.md`): one surface,
 * rounded at its top corners, with no divider or shadow, as tall as [dock] says, the bottom inset included where there
 * is no setlist's pager bar under it to cover that. Its rows are the screen's ([SongDockState.rows]) and their contents
 * the song's that the pager is on or heading for ([targetPage], [SongDockState.contents]), crossfading as a page turns, as the app
 * bar's subtitle does. Read by screen readers after the app bar and before the song.
 */
@Composable
internal fun SongDock(
    modifier: Modifier = Modifier,
    dock: SongDockState,
    contentPadding: PaddingValues,
    targetPage: Int,
) {
    // Kept while the dock leaves, so that it shrinks away as it was rather than as nothing.
    val lastLayout = remember { LastDockLayout() }
    dock.layout?.let { lastLayout.value = it }
    val layout = lastLayout.value ?: return
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(dock.height)
            .clipToBounds()
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(topStart = DOCK_CORNER, topEnd = DOCK_CORNER))
            .semantics {
                isTraversalGroup = true
                traversalIndex = DOCK_TRAVERSAL_INDEX
            },
    ) {
        val layoutDirection = LocalLayoutDirection.current
        val horizontalPadding = PaddingValues(
            start = contentPadding.calculateStartPadding(layoutDirection) + DOCK_HORIZONTAL_PADDING,
            end = contentPadding.calculateEndPadding(layoutDirection) + DOCK_HORIZONTAL_PADDING,
        )
        Crossfade(targetState = targetPage) { page ->
            DockRowsOf(
                content = dock.contents[page],
                rows = dock.rows,
                layout = layout,
                horizontalPadding = horizontalPadding,
            )
        }
    }
}

/**
 * The dock's rows for one song's [content], laid out at the dock's full height whatever height it is animating through:
 * the playing controls with the scale chips after them (and the chord chips between the two in [DockTier.CHORD_CHIPS]),
 * and the diagrams in a row of their own in the other tiers, as [layout] sizes them.
 */
@Composable
private fun DockRowsOf(
    content: DockContent?,
    rows: DockRows,
    layout: DockLayout,
    horizontalPadding: PaddingValues,
) = Column(
    modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight(align = Alignment.Top, unbounded = true)
        .padding(vertical = DOCK_VERTICAL_PADDING),
    verticalArrangement = Arrangement.spacedBy(DOCK_ROW_GAP),
) {
    val cells = content?.let { rememberChordCells(it.model, it.chordDiagrams) }.orEmpty()
    val onChordClicked = content?.chordDiagrams?.onChordClicked
    val isSmall = layout.tier == DockTier.SMALL_DIAGRAMS
    val hasDiagramRow = rows.hasChordRow && layout.tier != DockTier.CHORD_CHIPS
    if (rows.hasPlayingRow || rows.hasScaleRow || !hasDiagramRow) {
        DockRow(height = layout.rowHeight, horizontalPadding = horizontalPadding) {
            if (content != null) {
                DockPlayingControls(content)
                if (rows.hasChordRow && !hasDiagramRow) DockChordChips(cells = cells, onChordClicked = onChordClicked)
                DockScaleChips(content)
            }
        }
    }
    if (hasDiagramRow) {
        DockRow(height = layout.diagramRowHeight, horizontalPadding = horizontalPadding) {
            if (content != null) DockDiagramStrip(cells = cells, isSmall = isSmall, onChordClicked = onChordClicked)
        }
    }
}

/**
 * One row of the dock, [height] tall, scrolled sideways by hand only. It runs to the screen's edges, its content padded
 * in from them by [horizontalPadding], so it fades nowhere: a fade belongs only where a scrolling row meets something
 * that does not scroll.
 */
@Composable
private fun DockRow(
    height: Dp,
    horizontalPadding: PaddingValues,
    content: @Composable RowScope.() -> Unit,
) = Row(
    modifier = Modifier
        .fillMaxWidth()
        .height(height)
        .horizontalScroll(rememberScrollState())
        .padding(horizontalPadding),
    horizontalArrangement = Arrangement.spacedBy(DOCK_ITEM_GAP),
    verticalAlignment = Alignment.CenterVertically,
    content = content,
)

/**
 * Puts what it is applied to after the dock in the order a screen reader takes the screen in: the song's pages, which
 * are drawn above the dock but read after it.
 */
internal fun Modifier.readAfterDock() = semantics {
    isTraversalGroup = true
    traversalIndex = DOCK_TRAVERSAL_INDEX + 1f
}

/** The dock's layout as [SongDock] last drew it. Not a state. */
private class LastDockLayout(var value: DockLayout? = null)

private val DOCK_CORNER = 16.dp
private val DOCK_HORIZONTAL_PADDING = 16.dp
private val DOCK_ITEM_GAP = 8.dp

/** After the app bar's, which is the screen's first, and before the song's pages, which come after it. */
private const val DOCK_TRAVERSAL_INDEX = 1f
