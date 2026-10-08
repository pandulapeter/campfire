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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.filter

/**
 * Scrolls a list of a sheet or a dialog back to its start whenever [key], the order it is sorted in, changes: what was
 * in front before is somewhere else now, and the start is where the new order is read from.
 *
 * The reordered [contents] can arrive several frames after [key], where the view model sorts them away from the main
 * thread, and a lazy list follows its first visible item to wherever that went. So, as in [ScrollToTopWhenChanged],
 * every change of [contents] is held at the position the list is at until the list is next scrolled.
 */
@Composable
internal fun ScrollToStartWhenChanged(
    listState: LazyListState,
    key: Any?,
    contents: Any?,
) {
    val heldStart = remember { HeldStart(key, contents) }
    // A side effect for the reason given in ScrollToTopWhenChanged: it runs before the list measures the new contents.
    SideEffect {
        if (key != heldStart.key) {
            heldStart.key = key
            heldStart.isHolding = true
            listState.requestScrollToItem(0)
        } else if (heldStart.isHolding && contents !== heldStart.contents && !listState.isScrollInProgress) {
            listState.requestScrollToItem(
                index = listState.firstVisibleItemIndex,
                scrollOffset = listState.firstVisibleItemScrollOffset,
            )
        }
        heldStart.contents = contents
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.filter { it }.collect { heldStart.isHolding = false }
    }
}

/** What [ScrollToStartWhenChanged] last saw, which is not state: it is only read and written by its side effect. */
private class HeldStart(
    var key: Any?,
    var contents: Any?,
) {
    var isHolding = false
}
