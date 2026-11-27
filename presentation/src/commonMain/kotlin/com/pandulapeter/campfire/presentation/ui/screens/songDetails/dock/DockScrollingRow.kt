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
import androidx.compose.ui.platform.LocalLayoutDirection
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
 * [start] leaves what scrolls at least [minScrollingRoom] (the one row's [DOCK_MIN_SCROLLING_ROOM], [DockEndsLayout]), so
 * that a one-line card of long names is cut short rather than taking the row from the chips it is there for. The
 * diagrams' row keeps none: its cards have their own narrow size on a phone, and what is left between them is the
 * diagrams'.
 *
 * [start] and [end] fade and grow in and out as [isStartShown] and [isEndShown] say, the gap between them and what scrolls
 * going with them, so that a row gaining or losing one (a switch in the Preferences) gives or takes the room gradually.
 * [contentModifier] is the scrolling part's, after its scrolling and its padding.
 */
@Composable
internal fun DockScrollingRow(
    modifier: Modifier = Modifier,
    height: Dp,
    horizontalPadding: PaddingValues,
    scrollState: ScrollState,
    start: (@Composable () -> Unit)? = null,
    end: (@Composable () -> Unit)?,
    isStartShown: Boolean = true,
    isEndShown: Boolean = true,
    fadesAtEnd: Boolean = false,
    minScrollingRoom: Dp = 0.dp,
    contentModifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    val startPadding = horizontalPadding.calculateStartPadding(layoutDirection)
    val endPadding = horizontalPadding.calculateEndPadding(layoutDirection)
    DockEndsLayout(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        minMiddleRoom = minScrollingRoom,
        start = {
            if (start != null) {
                Row(modifier = Modifier.padding(start = startPadding), verticalAlignment = Alignment.CenterVertically) {
                    DockRowEnd(isShown = isStartShown, expandFrom = Alignment.Start) {
                        Row(modifier = Modifier.padding(end = DOCK_ITEM_GAP), verticalAlignment = Alignment.CenterVertically) { start() }
                    }
                }
            }
        },
        middle = {
            Row(
                modifier = Modifier
                    .fadingHorizontalEdges(
                        scrolledFromStart = if (start == null) null else { { scrollState.value } },
                        scrolledFromEnd = if (end == null && !fadesAtEnd) null else { { scrollState.maxValue - scrollState.value } },
                        backgroundColor = MaterialTheme.colorScheme.surfaceContainer,
                    )
                    .horizontalScroll(scrollState)
                    .padding(start = if (start == null) startPadding else 0.dp, end = if (end == null) endPadding else 0.dp)
                    .then(contentModifier),
                horizontalArrangement = Arrangement.spacedBy(DOCK_ITEM_GAP),
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        },
        end = {
            if (end != null) {
                Row(modifier = Modifier.padding(end = endPadding), verticalAlignment = Alignment.CenterVertically) {
                    DockRowEnd(isShown = isEndShown, expandFrom = Alignment.End) {
                        Row(modifier = Modifier.padding(start = DOCK_ITEM_GAP), verticalAlignment = Alignment.CenterVertically) { end() }
                    }
                }
            }
        },
    )
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
