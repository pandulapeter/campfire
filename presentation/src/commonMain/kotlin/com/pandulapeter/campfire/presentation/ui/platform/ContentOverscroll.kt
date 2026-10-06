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

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollDispatcher
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import com.pandulapeter.campfire.presentation.ui.components.fadingTopEdge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Lets content that fits its container stretch or bounce under a drag the way content that scrolls does. Foundation
 * only hands a drag to the overscroll effect while there is something to scroll, so a short list or form felt dead
 * under the finger. This takes the drags such content cannot scroll by and gives them to the effect Compose provides
 * for the platform (`rememberOverscrollEffect`: Android's stretch, iOS's bounce, and none on the desktop and the web,
 * where it does nothing); content that does scroll is left to Foundation's own.
 *
 * Content pulled up this way is carried to the top edge of its viewport, where a sheet's header or an app bar sits, and
 * fades out there as it would scrolled under them, as strong as it has been pulled: a container's own top fade follows
 * its scroll position, which content that fits never leaves. A container whose content fades somewhere other than its
 * viewport's top edge (the list screens' cards, under the pinned header) passes a [pull] of its own and fades by it
 * instead.
 */
@Composable
internal fun Modifier.bounceScrollableContent(
    state: ScrollableState,
    orientation: Orientation = Orientation.Vertical,
    pull: OverscrollPull? = null,
): Modifier {
    val effect = rememberOverscrollEffect()
    if (effect == null || state.canScrollForward || state.canScrollBackward) return this
    val dispatcher = remember { NestedScrollDispatcher() }
    val scope = rememberCoroutineScope()
    val ownPull = remember { OverscrollPull() }
    val usedPull = pull ?: ownPull
    val connection = remember(state, effect, orientation, dispatcher, scope, usedPull) {
        ShortContentOverscroll(state, effect, orientation, dispatcher, scope, usedPull)
    }
    return clipToBounds()
        .then(if (orientation == Orientation.Vertical && pull == null) Modifier.fadingTopEdge { ownPull.towardsStart.toInt() } else Modifier)
        .overscroll(effect)
        .nestedScroll(connection, dispatcher)
}

/**
 * How far the finger has pulled content that fits up past its end, which is how far the overscroll effect carries its
 * top under whatever sits above the container. The effect does not say how far it has moved the content, so this
 * follows the finger while it is down and settles back to nothing alongside the effect once it lets go.
 */
@Stable
internal class OverscrollPull {

    var towardsStart by mutableFloatStateOf(0f)
        internal set
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
    private val scope: CoroutineScope,
    private val overscrollPull: OverscrollPull,
) : NestedScrollConnection {
    private var hasPulled = false
    private var releaseJob: Job? = null

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (state.canScrollForward || state.canScrollBackward || source != NestedScrollSource.UserInput) return Offset.Zero
        val delta = if (orientation == Orientation.Vertical) Offset(0f, available.y) else Offset(available.x, 0f)
        // The parents are offered the drag first, as they would be offered what a list left over: a bottom sheet is
        // dragged down by its content from its post-scroll, which would otherwise only come after this took it all.
        val pull = delta - dispatcher.dispatchPostScroll(consumed = Offset.Zero, available = delta, source = source)
        if (pull != Offset.Zero) {
            hasPulled = true
            releaseJob?.cancel()
            overscrollPull.towardsStart = (overscrollPull.towardsStart - pull.y).coerceAtLeast(0f)
            effect.applyToScroll(pull, source) { Offset.Zero }
        }
        return delta
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        // A glow (before Android 12) reports no stretch distance, but still has to be told that the finger let go.
        if (!hasPulled && !effect.isInProgress) return Velocity.Zero
        hasPulled = false
        releaseJob?.cancel()
        if (overscrollPull.towardsStart > 0f) {
            releaseJob = scope.launch {
                animate(
                    initialValue = overscrollPull.towardsStart,
                    targetValue = 0f,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                ) { value, _ -> overscrollPull.towardsStart = value }
            }
        }
        val velocity = if (orientation == Orientation.Vertical) Velocity(0f, available.y) else Velocity(available.x, 0f)
        effect.applyToFling(velocity) { Velocity.Zero }
        return velocity
    }
}
