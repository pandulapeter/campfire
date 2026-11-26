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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.ui.components.fadingHorizontalEdges

/**
 * One row of the dock, [height] tall: [start] and [end] stand still at its ends (null where it has nothing there; the
 * instrument pill and the tempo chip where it has), and the [content] between them scrolls sideways ([scrollState]), by
 * hand, and back to what is on the screen after the dock reorders it (`ScrollWithDockOrder`). The content fades out where it meets either, as far as it can still scroll that way, and runs to the
 * screen's edges, padded in from them by
 * [horizontalPadding], where nothing stands there: a fade belongs only where a scrolling row meets something that does
 * not scroll, which is also what [fadesAtEnd] says stands past its end (the side panel the row under the song runs up
 * to).
 *
 * [end] is measured first and [start] with what it leaves but [minScrollingRoom] (the one row's [DOCK_MIN_SCROLLING_ROOM]),
 * so that a one-line instrument card with long names ("12-string guitar", "Full step down") is cut short rather than
 * taking the whole row from the chips it is there for (see [dockScrollingRowStartMaxWidth]). The diagrams' row keeps
 * none: its cards have their own narrow size on a phone, and what is left between them is the diagrams'.
 */
@Composable
internal fun DockScrollingRow(
    height: Dp,
    horizontalPadding: PaddingValues,
    scrollState: ScrollState,
    start: (@Composable () -> Unit)? = null,
    end: (@Composable () -> Unit)?,
    fadesAtEnd: Boolean = false,
    minScrollingRoom: Dp = 0.dp,
    content: @Composable RowScope.() -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    val startPadding = horizontalPadding.calculateStartPadding(layoutDirection)
    val endPadding = horizontalPadding.calculateEndPadding(layoutDirection)
    Layout(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
        contents = listOf(
            {
                if (start != null) {
                    Row(modifier = Modifier.padding(start = startPadding), verticalAlignment = Alignment.CenterVertically) { start() }
                }
            },
            {
                Row(
                    modifier = Modifier
                        .fadingHorizontalEdges(
                            scrolledFromStart = if (start == null) null else { { scrollState.value } },
                            scrolledFromEnd = if (end == null && !fadesAtEnd) null else { { scrollState.maxValue - scrollState.value } },
                            backgroundColor = MaterialTheme.colorScheme.surfaceContainer,
                        )
                        .horizontalScroll(scrollState)
                        .padding(start = if (start == null) startPadding else DOCK_ITEM_GAP, end = if (end == null) endPadding else DOCK_ITEM_GAP),
                    horizontalArrangement = Arrangement.spacedBy(DOCK_ITEM_GAP),
                    verticalAlignment = Alignment.CenterVertically,
                    content = content,
                )
            },
            {
                if (end != null) {
                    Row(modifier = Modifier.padding(end = endPadding), verticalAlignment = Alignment.CenterVertically) { end() }
                }
            },
        ),
    ) { (startMeasurables, contentMeasurables, endMeasurables), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val endPlaceables = endMeasurables.map { it.measure(loose) }
        val endWidth = endPlaceables.sumOf { it.width }
        val startMaxWidth = dockScrollingRowStartMaxWidth(constraints.maxWidth, endWidth, minScrollingRoom.roundToPx())
        val startPlaceables = startMeasurables.map { it.measure(loose.copy(maxWidth = startMaxWidth)) }
        val startWidth = startPlaceables.sumOf { it.width }
        val contentWidth = (constraints.maxWidth - startWidth - endWidth).coerceAtLeast(0)
        val contentPlaceables = contentMeasurables.map { it.measure(Constraints(minWidth = contentWidth, maxWidth = contentWidth, maxHeight = constraints.maxHeight)) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            val center = { placeableHeight: Int -> (constraints.maxHeight - placeableHeight) / 2 }
            startPlaceables.forEach { it.placeRelative(0, center(it.height)) }
            contentPlaceables.forEach { it.placeRelative(startWidth, center(it.height)) }
            endPlaceables.forEach { it.placeRelative(constraints.maxWidth - endWidth, center(it.height)) }
        }
    }
}

/**
 * How wide the start of a dock's row [rowWidth] wide may be beside an end [endWidth] wide, all in pixels: whatever
 * leaves the scrolling content between them [minScrollingRoom], but never less than nothing.
 */
internal fun dockScrollingRowStartMaxWidth(rowWidth: Int, endWidth: Int, minScrollingRoom: Int) =
    (rowWidth - endWidth - minScrollingRoom).coerceAtLeast(0)

/** Between the items of a row of the dock, and between its scrolling part and what stands at its ends. */
internal val DOCK_ITEM_GAP = 8.dp

/** What a row's scrolling content keeps beside what stands at its ends: about two chord chips and their gaps. */
internal val DOCK_MIN_SCROLLING_ROOM = 120.dp
