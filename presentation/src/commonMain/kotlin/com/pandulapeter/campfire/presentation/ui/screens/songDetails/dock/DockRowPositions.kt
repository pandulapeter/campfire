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
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

/**
 * Where a row of the dock that scrolls itself on a page turn placed the items it scrolls to, by an id of the row's own
 * (the diagram strip's chords and key labels, the scale row's groups). Not a state: read only after placing.
 */
internal class DockRowPositions {
    private val starts = mutableMapOf<String, Pair<Float, Int>>()

    fun record(id: String, x: Float, width: Int) {
        starts[id] = x to width
    }

    /** How far the row scrolls to bring [id] to where its first item rests, its start being the right in [layoutDirection] Rtl. */
    fun scrollOffsetOf(id: String, layoutDirection: LayoutDirection): Int? {
        val (x, width) = starts[id] ?: return null
        return if (layoutDirection == LayoutDirection.Rtl) {
            (starts.values.maxOf { (start, size) -> start + size } - (x + width)).toInt()
        } else {
            (x - starts.values.minOf { it.first }).toInt()
        }
    }
}

/**
 * Scrolls a row of the dock ([scrollState]) to the item [positions] placed as [id] whenever [id] changes, and only then:
 * a row swiped by hand stays where it was left until the next page turn changes what it is headed for.
 */
@Composable
internal fun ScrollToOnPageTurn(id: String?, scrollState: ScrollState, positions: DockRowPositions) {
    val layoutDirection = LocalLayoutDirection.current
    LaunchedEffect(id, scrollState, positions) {
        if (id == null) return@LaunchedEffect
        // Placed by the next frame, a page with a row new to it included.
        withFrameNanos {}
        if (scrollState.isScrollInProgress) return@LaunchedEffect
        positions.scrollOffsetOf(id, layoutDirection)?.let { scrollState.animateScrollTo(it.coerceIn(0, scrollState.maxValue)) }
    }
}

/**
 * Brings the item [requester] is on into view in the side panel ([scrollState], the panel's) whenever [id], the item it
 * is on, changes, and only then, scrolling no further than that: a panel scrolled by hand stays where it was left until
 * the next page turn, and one whose every item is in view does not move.
 */
@Composable
internal fun BringIntoViewOnPageTurn(id: String?, scrollState: ScrollState, requester: BringIntoViewRequester) {
    LaunchedEffect(id, scrollState, requester) {
        if (id == null) return@LaunchedEffect
        // The requester is on the new item by the next frame.
        withFrameNanos {}
        if (scrollState.isScrollInProgress) return@LaunchedEffect
        requester.bringIntoView()
    }
}
