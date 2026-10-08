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

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember

/**
 * Holds the rows below the selected group where they are on screen while the group above them grows, so the row that
 * was just tapped stays under the finger when its copy joins the group. A lazy list keeps its first visible item in
 * place by key, which only helps while that item is below the group; with the group or its heading at the top of the
 * viewport, everything after it would be pushed down by a row. So the first visible row of the rest is pinned at its
 * offset instead, by index, the content above it moving up out of the way.
 *
 * Call it after any other effect that positions the same list ([ScrollToStartWhenChanged]): the last request of a
 * frame is the one the list measures with.
 */
@Composable
internal fun KeepChecklistRowsInPlace(listState: LazyListState, layout: ChecklistLayout) {
    val previous = remember(listState) { PreviousChecklistLayout(layout) }
    SideEffect {
        // The layout info is still the last measured one, from before the group grew.
        listState.layoutInfo.visibleItemsInfo
            .firstOrNull { (it.key as? String)?.startsWith(ROW_KEY_PREFIX) == true }
            ?.let { anchor ->
                layout.anchorIndexAfter(previous.layout, (anchor.key as String).removePrefix(ROW_KEY_PREFIX), anchor.index)
                    ?.let { listState.requestScrollToItem(index = it, scrollOffset = -anchor.offset) }
            }
        previous.layout = layout
    }
}

/** What [KeepChecklistRowsInPlace] last saw, which is not state: it is only read and written by its side effect. */
private class PreviousChecklistLayout(var layout: ChecklistLayout)
