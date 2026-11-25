/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails.stepping

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Makes the whole of the area the song is read in the step buttons' touch target: a tap on its top half steps back
 * ([onStep] with -1), a tap on its bottom half forward (1), wherever that has anywhere to go. A song turned a column at a
 * time ([stepsAcross] not null) is split into its left and right halves instead, the left one stepping back unless the
 * song reads from the right ([stepsAcross] true), and a sloppy tap's travel is measured against the width. A reader with both hands on
 * an instrument has a moment to reach for the screen and none to aim at a small button in its corner.
 *
 * Only a tap nothing else wanted counts. The modifier sits on the parent of the pager and the buttons, so everything
 * inside it hears the press first: a press a fold toggle or a button took, and a pinch, which takes a second finger, are
 * left alone, and so is a press held for a long press. A press that lands while the song or the pager is still moving on
 * its own ([isMovingFreely]) is what stops a fling, and is left to do only that; one landing during a step of the song
 * ([isStepping]) is a step on from where that one is headed, as a second press of a button is.
 *
 * The song's scroll takes a press that lands while it is moving, a step included, to stop itself: it consumes the down
 * and drags from the first pixel, and the release flings it to the nearest stop. So the press is looked at in the Initial
 * pass, before the scroll has seen it, and one that lands during a step is told apart from a drag by how far it travelled
 * rather than by whether the scroll took it, and steps a frame after its release, like a sloppy tap below.
 *
 * A tap made between two strums often slides, and the scroll takes it past the touch slop as a drag. One that is over
 * within [SLOPPY_TAP_MAX_MILLIS] and travelled less than [SLOPPY_TAP_MAX_TRAVEL_FRACTION] of the area's height is still
 * taken for a tap, by where it landed rather than by the way it slid, since the slide is the accident: the step is counted
 * from where the song was when the finger came down ([stepOrigin]), and replaces whatever the release flung it into a
 * frame later, once the fling has started and can be cancelled rather than cancelling the step. A slide that was mostly
 * sideways is the pager's, which may turn the page on it, so that one only steps once the pager has settled back on the
 * song it started on.
 */
@Composable
internal fun Modifier.stepOnTap(
    pagerState: PagerState,
    isMovingFreely: () -> Boolean,
    isStepping: () -> Boolean,
    stepOrigin: () -> Int?,
    stepsAcross: () -> Boolean?,
    onStep: (direction: Int, from: Int?) -> Unit,
): Modifier {
    val currentIsMovingFreely by rememberUpdatedState(isMovingFreely)
    val currentIsStepping by rememberUpdatedState(isStepping)
    val currentStepOrigin by rememberUpdatedState(stepOrigin)
    val currentStepsAcross by rememberUpdatedState(stepsAcross)
    val currentOnStep by rememberUpdatedState(onStep)
    return pointerInput(pagerState) {
        coroutineScope {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                // A right or middle click is a context menu or a paste, not a reach for the next line. Touch and stylus
                // presses report no mouse buttons (Android's button state is 0 for a finger), so only a mouse is asked
                // which one it was; currentEvent is the event the down came in.
                val isOtherMouseButton = down.type == PointerType.Mouse && !currentEvent.buttons.isPrimaryPressed
                if (isOtherMouseButton) return@awaitEachGesture
                if (currentIsMovingFreely()) return@awaitEachGesture logDroppedTap("the song was moving freely")
                val isSteppingOn = currentIsStepping()
                val origin = currentStepOrigin()
                val readsLeftwards = currentStepsAcross()
                val page = pagerState.currentPage
                // The same press once everything inside has seen it. During a step the scroll is what took it, and a
                // button that did is still told by the up it takes.
                val mainDown = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                if (mainDown.isConsumed && !isSteppingOn) return@awaitEachGesture
                var isDragged = false
                var up: PointerInputChange
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.changes.any { it.id != down.id && it.pressed }) return@awaitEachGesture logDroppedTap("a second pointer")
                    val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture logDroppedTap("the pointer was lost")
                    if (!change.pressed) {
                        up = change
                        break
                    }
                    if (if (isSteppingOn) (change.position - down.position).getDistance() > viewConfiguration.touchSlop else change.isConsumed) isDragged = true
                }
                // An up taken without a drag before it is a child's tap, which was answered there. After a drag nothing
                // inside takes the up, since the scroll and the pager only fling on it, so a consumed one is the
                // cancellation Compose makes up when the system takes the pointer away: Android's home and back
                // gestures start as an ordinary press and slide on the window, and are short enough to pass for a tap.
                // During a step the scroll took the press, which it treats as a drag from the first pixel, and it takes
                // the up too to fling to the nearest stop, so there an unmoved press with a consumed up is still a tap.
                if (up.isConsumed && (isDragged || !isSteppingOn)) {
                    if (isDragged) logDroppedTap("cancelled by the system")
                    return@awaitEachGesture
                }
                val direction = when (readsLeftwards) {
                    null -> if (down.position.y < size.height / 2f) -1 else 1
                    false -> if (down.position.x < size.width / 2f) -1 else 1
                    true -> if (down.position.x < size.width / 2f) 1 else -1
                }
                val extent = if (readsLeftwards == null) size.height else size.width
                val duration = up.uptimeMillis - down.uptimeMillis
                val isSideways: Boolean
                if (!isDragged) {
                    if (duration >= viewConfiguration.longPressTimeoutMillis) return@awaitEachGesture logDroppedTap("a long press ($duration ms)")
                    up.consume()
                    if (!isSteppingOn) {
                        currentOnStep(direction, origin)
                        return@awaitEachGesture
                    }
                    isSideways = false
                } else {
                    val travel = up.position - down.position
                    val reach = travel.getDistance() / extent
                    if (duration > SLOPPY_TAP_MAX_MILLIS || reach > SLOPPY_TAP_MAX_TRAVEL_FRACTION) {
                        // Only a near miss is logged: a slide over twice either limit is a drag or a fling, made on purpose.
                        if (duration <= SLOPPY_TAP_MAX_MILLIS * 2 && reach <= SLOPPY_TAP_MAX_TRAVEL_FRACTION * 2) {
                            logDroppedTap("a slide of $duration ms and ${(reach * 100).roundToInt()}% of the ${if (readsLeftwards == null) "height" else "width"}")
                        }
                        return@awaitEachGesture
                    }
                    isSideways = abs(travel.x) > abs(travel.y)
                }
                launch {
                    withFrameNanos { }
                    if (isSideways) {
                        snapshotFlow { pagerState.isScrollInProgress }.first { !it }
                        if (pagerState.currentPage != page) return@launch logDroppedTap("the slide turned the page")
                    }
                    currentOnStep(direction, origin)
                }
            }
        }
    }
}

/**
 * A breadcrumb for a press on the song that did not step it, by the [reason] it was dropped for, so that a reader's report
 * of taps going missing can be told from the log. Only presses that could have been meant as a tap are logged: one a
 * button or another child took was answered there, and a right or middle click is no tap.
 */
private fun logDroppedTap(reason: String) = println("Step tap dropped: $reason")

private const val SLOPPY_TAP_MAX_MILLIS = 250L
private const val SLOPPY_TAP_MAX_TRAVEL_FRACTION = 0.1f
