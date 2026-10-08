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

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.filter

/**
 * Scrolls a list back to the top whenever [key] changes - which is where its search, its sorting and its filters
 * go - and keeps the change of [contents] that follows it animated.
 *
 * A lazy grid holds on to the key of its first visible item across a change of its contents, and a list that grows
 * has that item further down than it was: a filter taken off puts the songs it hid above the row at the top, and the
 * grid follows that row to its new index. Going back to the top from there is a jump to a different position, which
 * the grid answers by forgetting where every item was, so nothing slides out of the way and nothing fades in - the
 * rows are simply there. A list that is narrowed never runs into it, since the rows it keeps only ever move up.
 *
 * The position is therefore asked for by index, in the very composition the new contents arrive in, before the grid
 * measures them. That can be several frames after [key] changed, since the list is worked out outside the
 * composition, so every change of [contents] is held that way until the list is next scrolled - which is also where
 * holding it by index stops being right, and the grid goes back to following its first visible row through an edit.
 * The key last scrolled to the top is saved, so that coming back from another screen keeps the restored position
 * rather than jumping to the top.
 *
 * A change of [key] that was asked for from a row of the list itself - a tag on a song, which narrows the list to the
 * songs carrying it - leaves that row where it was instead, since the row is what the user was looking at and it is
 * still in the list the change leads to. The row is put in [anchor] before the change is made, and the position is
 * asked for once the contents it changes arrive: by the row's new index, with the offset it had on screen, which the
 * grid honors as far as there is list above the row to fill it with and stops at the top where there is not. That
 * is a jump to a different position, so the grid animates none of it, and [anchor] narrates the change instead (see
 * [anchoredTransition]).
 *
 * @param contents What the grid is built from, compared by identity: a new instance is a change of the list. Where an
 * [anchor] is passed, a value that changes with [key] and carries it, so that the two arrive in one composition: the
 * anchor is only consumed by a change of [contents], and a key that changed without one - a filter that leaves the
 * list as it was - would leave the row pending until the list next changes for some unrelated reason.
 * @param itemIndex The index of the item with the given key in [contents], or null where it holds no such item.
 */
@Composable
internal fun ScrollToTopWhenChanged(
    listState: LazyGridState,
    key: String,
    contents: Any?,
    anchor: ListAnchor? = null,
    itemIndex: (Any) -> Int? = { null },
    scrollToTopOnKeyChange: Boolean = true,
) {
    var lastScrollToTopKey by rememberSaveable { mutableStateOf(key) }
    val heldTop = remember { HeldTop(contents) }
    val coroutineScope = rememberCoroutineScope()
    // A side effect rather than a launched one, because it runs before the grid measures what this composition gave
    // it: a request made a frame later would come after the grid had already followed its first row down.
    SideEffect {
        val hasKeyChanged = key != lastScrollToTopKey
        if (hasKeyChanged) {
            lastScrollToTopKey = key
            // Opting out lets the grid retain its visible item by key when a filter is removed.
            heldTop.isHolding = scrollToTopOnKeyChange
            heldTop.anchoredItem = anchor?.take()
        }
        val anchoredItem = heldTop.anchoredItem
        if (anchoredItem != null) {
            if (contents !== heldTop.contents) {
                heldTop.anchoredItem = null
                val index = itemIndex(anchoredItem.key)
                if (index == null) {
                    listState.requestScrollToItem(0)
                } else {
                    listState.requestScrollToItem(index = index, scrollOffset = -anchoredItem.offset)
                    anchor?.animateFrom(anchoredItem.visibleOffsets, coroutineScope)
                }
            }
        } else if (hasKeyChanged && scrollToTopOnKeyChange) {
            listState.requestScrollToItem(0)
        } else if (heldTop.isHolding && contents !== heldTop.contents && !listState.isScrollInProgress) {
            listState.requestScrollToItem(
                index = listState.firstVisibleItemIndex,
                scrollOffset = listState.firstVisibleItemScrollOffset,
            )
        }
        heldTop.contents = contents
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.filter { it }.collect {
            heldTop.isHolding = false
            // A list scrolled before the change arrived has moved on from the row, and bringing it back would undo
            // the scroll.
            heldTop.anchoredItem = null
            anchor?.stopTransition()
        }
    }
}

/** What [ScrollToTopWhenChanged] remembers between compositions, none of which is ever drawn. */
private class HeldTop(var contents: Any?) {
    var isHolding = false
    var anchoredItem: AnchoredItem? = null
}
