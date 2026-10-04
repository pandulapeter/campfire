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

import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.OverscrollFactory
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Use native effects where provided. */
@Composable
internal fun rememberContentOverscrollFactory(): OverscrollFactory? {
    return if (hasNativeOverscroll) LocalOverscrollFactory.current else null
}

internal expect val hasNativeOverscroll: Boolean
internal expect fun PointerEvent.overscrollWheelDelta(bounds: IntSize, density: Float): Offset

/**
 * Foundation skips bounce on content that fits. This bridge handles those drags; scrollable content uses its own
 * Foundation effect from LocalOverscrollFactory. Desktop/web wheel events need a separate boundary path because
 * Foundation's wheel scroller bypasses overscroll and rejects events once the content reaches its boundary.
 */
@Composable
internal fun Modifier.bounceScrollableContent(state: ScrollableState, orientation: Orientation = Orientation.Vertical): Modifier {
    if (hasNativeOverscroll && (state.canScrollForward || state.canScrollBackward)) return this
    val effect = rememberOverscrollEffect() ?: return this
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current.density
    var bounds by remember { mutableStateOf(IntSize.Zero) }
    val connection = remember(state, effect, orientation) {
        object : NestedScrollConnection {
            private var hasPulled = false
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (state.canScrollForward || state.canScrollBackward || source != NestedScrollSource.UserInput) return Offset.Zero
                val delta = if (orientation == Orientation.Vertical) Offset(0f, available.y) else Offset(available.x, 0f)
                if (delta != Offset.Zero) hasPulled = true
                effect.applyToScroll(delta, source) { Offset.Zero }
                return delta
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                // Pre-Android 12 glow effects report no stretch distance but still require a release signal.
                if (!hasPulled && !effect.isInProgress) return Velocity.Zero
                hasPulled = false
                val velocity = if (orientation == Orientation.Vertical) Velocity(0f, available.y) else Velocity(available.x, 0f)
                effect.applyToFling(velocity) { Velocity.Zero }
                return velocity
            }
        }
    }
    val bounce = clipToBounds().overscroll(effect).nestedScroll(connection)
    if (hasNativeOverscroll) return bounce
    return bounce.onSizeChanged { bounds = it }.pointerInput(state, effect, orientation, density) {
        var release: Job? = null
        try {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.type != PointerEventType.Scroll || event.changes.any { it.isConsumed } ||
                        event.keyboardModifiers.isCtrlPressed || event.keyboardModifiers.isMetaPressed) continue
                    val delta = event.overscrollWheelDelta(bounds, density)
                    val axis = if (orientation == Orientation.Vertical) delta.y else delta.x
                    val other = if (orientation == Orientation.Vertical) delta.x else delta.y
                    if (axis == 0f || abs(axis) < abs(other)) continue
                    // Positive pointer motion moves content towards its start. Preserve the original wheel event
                    // whenever there is content to scroll: sticky headers and nested app bars stay under Foundation.
                    val canScroll = if (axis > 0f) state.canScrollBackward else state.canScrollForward
                    if (!canScroll) {
                        release?.cancel()
                        val pull = if (orientation == Orientation.Vertical) Offset(0f, axis) else Offset(axis, 0f)
                        effect.applyToScroll(pull, NestedScrollSource.UserInput) { Offset.Zero }
                        event.changes.forEach { it.consume() }
                    }
                    if (effect.isInProgress) {
                        release?.cancel()
                        // Start returning immediately instead of waiting for wheel momentum to become idle.
                        // Each following edge event interrupts the spring through applyToScroll, as on iOS.
                        release = scope.launch { effect.applyToFling(Velocity.Zero) { Velocity.Zero } }
                    }
                }
            }
        } finally {
            release?.cancel()
        }
    }
}

/** Explicit scroll containers share one state with their short-content bridge, even when state is created inline. */
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
