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

/**
 * The dock as a side panel at the end of the song (this package's `CLAUDE.md`), where a wide window puts it
 * ([isDockSidePanel]): one surface as tall as the song, rounded at its top start corner, as wide as [dock] says, the end
 * inset of [contentPadding] included. It grows in from the screen's end edge and shrinks back into it as the window
 * switches between the panel and the rows under the song, its content laid out at its full width all the while, so that
 * it slides rather than reflows. Its contents are the song's that the pager is on or heading for ([targetPage]),
 * crossfading as a page turns: under an import preview's [card], the controls stay put ([DockPanelControls]) and the
 * scale chips and diagrams scroll under them where the window is too short for them ([DockPanelRows]), fading out under
 * the controls, and at the bottom only over the window's bottom inset, which they scroll on under and are padded by;
 * [lastRow] is a setlist's pager bar, which ends the panel below what scrolls. Read by screen readers
 * after the app bar and before the song, as the rows under the song are.
 */
@Composable
internal fun DockSidePanel(
    modifier: Modifier = Modifier,
    dock: SongDockState,
    metronome: DockMetronome,
    contentPadding: PaddingValues,
    targetPage: Int,
    card: @Composable () -> Unit,
    lastRow: (@Composable () -> Unit)?,
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
            // Over a setlist's pager bar the panel ends at the bar, not at the window's edge.
            val bottomInset = if (lastRow == null) contentPadding.calculateBottomPadding() else 0.dp
            Column(modifier = Modifier.fillMaxWidth().padding(end = endInset)) {
                card()
                Crossfade(targetState = targetPage) { page ->
                    DockPanelControls(
                        modifier = Modifier.padding(horizontal = DOCK_PANEL_PADDING).padding(top = DOCK_VERTICAL_PADDING),
                        content = dock.contents[page],
                        metronome = metronome,
                        layout = layout,
                    )
                }
            }
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .fadingOverNavigationBar(scrollState = scrollState, backgroundColor = MaterialTheme.colorScheme.surfaceContainer, bottomInset = bottomInset)
                    .verticalScroll(scrollState)
                    .padding(end = endInset, bottom = bottomInset),
            ) {
                Crossfade(targetState = targetPage) { page ->
                    DockPanelRows(
                        // As far from the controls and the panel's end as the rows under the song are from each other
                        // and from their surface's edge, and as the panel's sides are (DOCK_PANEL_PADDING).
                        modifier = Modifier.padding(top = DOCK_ROW_GAP, bottom = DOCK_VERTICAL_PADDING),
                        content = dock.contents[page],
                        stepper = dock.steppers[page],
                        scrollState = scrollState,
                        rows = dock.rows,
                        layout = layout,
                    )
                }
            }
            lastRow?.invoke()
        }
    }
}

/** The side panel's layout and width as [DockSidePanel] last drew them. Not a state. */
private class LastDockPanel(var layout: DockLayout? = null, var width: Dp = 0.dp)
