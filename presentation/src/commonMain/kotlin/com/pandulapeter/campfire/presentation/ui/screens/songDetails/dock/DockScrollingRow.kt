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
 * hand only. The content fades out where it meets either, as far as it can still scroll that way, and runs to the
 * screen's edges, padded in from them by
 * [horizontalPadding], where nothing stands there: a fade belongs only where a scrolling row meets something that does
 * not scroll, which is also what [fadesAtEnd] says stands past its end (the side panel the row under the song runs up
 * to).
 */
@Composable
internal fun DockScrollingRow(
    height: Dp,
    horizontalPadding: PaddingValues,
    scrollState: ScrollState,
    start: (@Composable () -> Unit)? = null,
    end: (@Composable () -> Unit)?,
    fadesAtEnd: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) = Row(
    modifier = Modifier
        .fillMaxWidth()
        .height(height),
    verticalAlignment = Alignment.CenterVertically,
) {
    val layoutDirection = LocalLayoutDirection.current
    val startPadding = horizontalPadding.calculateStartPadding(layoutDirection)
    val endPadding = horizontalPadding.calculateEndPadding(layoutDirection)
    if (start != null) {
        Row(
            modifier = Modifier.padding(start = startPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            start()
        }
    }
    Row(
        modifier = Modifier
            .weight(1f)
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
    if (end != null) {
        Row(
            modifier = Modifier.padding(end = endPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            end()
        }
    }
}

/** Between the items of a row of the dock, and between its scrolling part and what stands at its ends. */
internal val DOCK_ITEM_GAP = 8.dp
