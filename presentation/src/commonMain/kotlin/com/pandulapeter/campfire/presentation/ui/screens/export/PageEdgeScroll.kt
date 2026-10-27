/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.export

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollDispatcher
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Hands the list the pages are an item of what a zoomed page cannot pan any further up or down: a drag that has brought
 * the page's top or bottom edge into view goes on scrolling the options, and a release there flings them, the way a
 * scrolling area nested in another hands over at its end. Only a drag of one finger does: the centroid of a pinch moves
 * as the fingers spread, and the list scrolling under a pinch would carry the page away from them. The page always pans
 * first, so a drag turning back moves the page away from its edge before the list.
 */
internal class PageEdgeScroll(private val coroutineScope: CoroutineScope) {
    private val dispatcher = NestedScrollDispatcher()
    private val velocityTracker = VelocityTracker()
    private var isPinching = false

    /** Whether the last movement of the drag reached the list, which decides whether its release flings it. */
    private var isHandingOver = false

    /** Gives the list the vertical part of a one-finger drag's [unused] pan, what the page's edge stopped. */
    fun handOver(unused: Offset) {
        isHandingOver = !isPinching && unused.y != 0f
        if (isHandingOver) {
            dispatcher.dispatchPostScroll(Offset.Zero, Offset(0f, unused.y), NestedScrollSource.UserInput)
        }
    }

    /**
     * Connects the page to the list and follows the pointers in the initial pass, before `transformable` takes them in the
     * main one, for how fast the finger was going as it lifted: `transformable` says how far, never how fast.
     */
    val modifier = Modifier.nestedScroll(NO_CONNECTION, dispatcher).pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            velocityTracker.resetTracking()
            velocityTracker.addPointerInputChange(down)
            isPinching = false
            isHandingOver = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.count { it.pressed } > 1) isPinching = true
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                velocityTracker.addPointerInputChange(change)
                if (!change.pressed) break
            }
            if (isHandingOver && !isPinching) {
                val velocity = velocityTracker.calculateVelocity()
                coroutineScope.launch { dispatcher.dispatchPostFling(Velocity.Zero, Velocity(0f, velocity.y)) }
            }
            isHandingOver = false
        }
    }
}

/** A [PageEdgeScroll] flinging in the composition's scope, so a fling still running stops as the screen leaves. */
@Composable
internal fun rememberPageEdgeScroll(): PageEdgeScroll {
    val coroutineScope = rememberCoroutineScope()
    return remember(coroutineScope) { PageEdgeScroll(coroutineScope) }
}

/** The page only dispatches: nothing it holds scrolls on behalf of what is nested in it. */
private val NO_CONNECTION = object : NestedScrollConnection {}
