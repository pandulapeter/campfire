/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.platform

import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.overscroll
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollDispatcher
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity

/**
 * Lets content that fits its container stretch or bounce under a drag the way content that scrolls does. Foundation
 * only hands a drag to the overscroll effect while there is something to scroll, so a short list or form felt dead
 * under the finger. This takes the drags such content cannot scroll by and gives them to the effect Compose provides
 * for the platform (`rememberOverscrollEffect`: Android's stretch, iOS's bounce, and none on the desktop and the web,
 * where it does nothing); content that does scroll is left to Foundation's own.
 */
@Composable
internal fun Modifier.bounceScrollableContent(state: ScrollableState, orientation: Orientation = Orientation.Vertical): Modifier {
    val effect = rememberOverscrollEffect()
    if (effect == null || state.canScrollForward || state.canScrollBackward) return this
    val dispatcher = remember { NestedScrollDispatcher() }
    val connection = remember(state, effect, orientation, dispatcher) { ShortContentOverscroll(state, effect, orientation, dispatcher) }
    return clipToBounds().overscroll(effect).nestedScroll(connection, dispatcher)
}

/** Explicit scroll containers share one state with their short-content bounce, even when state is created inline. */
@Composable
internal fun Modifier.bounceVerticalScroll(
    state: ScrollState = rememberScrollState(),
    enabled: Boolean = true,
    flingBehavior: FlingBehavior? = null,
    reverseScrolling: Boolean = false,
): Modifier = (if (enabled) bounceScrollableContent(state) else this)
    .verticalScroll(state, enabled = enabled, flingBehavior = flingBehavior, reverseScrolling = reverseScrolling)

@Composable
internal fun Modifier.bounceHorizontalScroll(
    state: ScrollState = rememberScrollState(),
    enabled: Boolean = true,
    flingBehavior: FlingBehavior? = null,
    reverseScrolling: Boolean = false,
): Modifier = (if (enabled) bounceScrollableContent(state, Orientation.Horizontal) else this)
    .horizontalScroll(state, enabled = enabled, flingBehavior = flingBehavior, reverseScrolling = reverseScrolling)

private class ShortContentOverscroll(
    private val state: ScrollableState,
    private val effect: OverscrollEffect,
    private val orientation: Orientation,
    private val dispatcher: NestedScrollDispatcher,
) : NestedScrollConnection {
    private var hasPulled = false

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (state.canScrollForward || state.canScrollBackward || source != NestedScrollSource.UserInput) return Offset.Zero
        val delta = if (orientation == Orientation.Vertical) Offset(0f, available.y) else Offset(available.x, 0f)
        // The parents are offered the drag first, as they would be offered what a list left over: a bottom sheet is
        // dragged down by its content from its post-scroll, which would otherwise only come after this took it all.
        val pull = delta - dispatcher.dispatchPostScroll(consumed = Offset.Zero, available = delta, source = source)
        if (pull != Offset.Zero) {
            hasPulled = true
            effect.applyToScroll(pull, source) { Offset.Zero }
        }
        return delta
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        // A glow (before Android 12) reports no stretch distance, but still has to be told that the finger let go.
        if (!hasPulled && !effect.isInProgress) return Velocity.Zero
        hasPulled = false
        val velocity = if (orientation == Orientation.Vertical) Velocity(0f, available.y) else Velocity(available.x, 0f)
        effect.applyToFling(velocity) { Velocity.Zero }
        return velocity
    }
}
