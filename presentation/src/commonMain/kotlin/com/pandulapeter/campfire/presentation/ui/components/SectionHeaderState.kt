/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components

import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.offset

/**
 * The fraction of the outgoing header still visible in the grid, and how far into the pinned position at the top of
 * the grid the header has come - which is where the app bar's buttons are over it.
 */
internal data class SectionHeaderState(
    val visibleFraction: Float,
    val pinnedFraction: Float,
)

/** The outgoing pinned header's position in the grid, including the part already above its viewport. */
internal data class PushedSectionHeader(
    val key: Any,
    val index: Int,
    val offset: IntOffset,
    val width: Int,
    val pushedDistance: Int,
    val visibleFraction: Float,
)

/**
 * The header of [contentType] that the next one is pushing up out of the grid, null while none is. A state rather than
 * its value, since the value changes on every frame of a push and whoever reads it is recomposed with it: only the copy
 * drawn over the grid should be, and only once per section, by reading the rest while it is laid out and drawn
 * ([pushedSectionHeaderPlacement]).
 */
@Composable
internal fun pushedSectionHeader(listState: LazyGridState, contentType: String): State<PushedSectionHeader?> =
    remember(listState, contentType) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val top = layoutInfo.viewportStartOffset
            layoutInfo.visibleItemsInfo.firstOrNull { item ->
                item.contentType == contentType && item.size.height > 0 && item.offset.y < top && item.offset.y + item.size.height > top
            }?.let { item ->
                PushedSectionHeader(
                    key = item.key,
                    index = item.index,
                    offset = item.offset,
                    width = item.size.width,
                    pushedDistance = top - item.offset.y,
                    visibleFraction = (item.offset.y + item.size.height - top).toFloat() / item.size.height,
                )
            }
        }
    }

/**
 * Puts the copy of a [pushed] header where the header is in the grid and makes it as wide, reading both while it is
 * laid out, so that the push moves it without recomposing it. Nothing is laid out once there is no header to follow.
 */
internal fun Modifier.pushedSectionHeaderPlacement(pushed: State<PushedSectionHeader?>) = layout { measurable, constraints ->
    val header = pushed.value ?: return@layout layout(0, 0) {}
    val width = header.width.coerceIn(constraints.minWidth, constraints.maxWidth)
    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    layout(placeable.width, placeable.height) { placeable.placeRelativeWithLayer(header.offset) }
}

/**
 * The scroll fractions that fade the outgoing header as the next one pushes it away, and narrow a pinned one. A state
 * rather than its value for the reason [pushedSectionHeader] is: [SectionHeader] reads it while it is laid out and
 * drawn, and the header item is not recomposed on every frame of a push.
 */
@Composable
internal fun rememberSectionHeaderState(listState: LazyGridState, headerIndex: Int): State<SectionHeaderState> =
    remember(listState, headerIndex) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val header = layoutInfo.visibleItemsInfo.firstOrNull { it.index == headerIndex }
            if (header == null || header.size.height <= 0) {
                SectionHeaderState(visibleFraction = 1f, pinnedFraction = 0f)
            } else {
                val top = layoutInfo.viewportStartOffset
                SectionHeaderState(
                    visibleFraction = ((header.offset.y + header.size.height - top).toFloat() / header.size.height).coerceIn(0f, 1f),
                    pinnedFraction = 1f - ((header.offset.y - top).toFloat() / header.size.height).coerceIn(0f, 1f),
                )
            }
        }
    }
