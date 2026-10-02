/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails

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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Makes the whole of the area the song is read in the step buttons' touch target: a tap on its top half steps back
 * ([onStep] with -1), a tap on its bottom half forward (1), wherever that has anywhere to go. A reader with both hands on
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
    onStep: (direction: Int, from: Int?) -> Unit,
): Modifier {
    val currentIsMovingFreely by rememberUpdatedState(isMovingFreely)
    val currentIsStepping by rememberUpdatedState(isStepping)
    val currentStepOrigin by rememberUpdatedState(stepOrigin)
    val currentOnStep by rememberUpdatedState(onStep)
    return pointerInput(pagerState) {
        coroutineScope {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                // A right or middle click is a context menu or a paste, not a reach for the next line. Touch and stylus
                // presses report no mouse buttons (Android's button state is 0 for a finger), so only a mouse is asked
                // which one it was; currentEvent is the event the down came in.
                val isOtherMouseButton = down.type == PointerType.Mouse && !currentEvent.buttons.isPrimaryPressed
                if (isOtherMouseButton || currentIsMovingFreely()) return@awaitEachGesture
                val isSteppingOn = currentIsStepping()
                val origin = currentStepOrigin()
                val page = pagerState.currentPage
                // The same press once everything inside has seen it. During a step the scroll is what took it, and a
                // button that did is still told by the up it takes.
                val mainDown = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                if (mainDown.isConsumed && !isSteppingOn) return@awaitEachGesture
                var isDragged = false
                var up: PointerInputChange
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.changes.any { it.id != down.id && it.pressed }) return@awaitEachGesture
                    val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                    if (!change.pressed) {
                        up = change
                        break
                    }
                    if (if (isSteppingOn) (change.position - down.position).getDistance() > viewConfiguration.touchSlop else change.isConsumed) isDragged = true
                }
                val direction = if (down.position.y < size.height / 2f) -1 else 1
                val duration = up.uptimeMillis - down.uptimeMillis
                val isSideways: Boolean
                if (!isDragged) {
                    // An up taken without a drag before it is a child's tap, which was answered there.
                    if (up.isConsumed || duration >= viewConfiguration.longPressTimeoutMillis) return@awaitEachGesture
                    up.consume()
                    if (!isSteppingOn) {
                        currentOnStep(direction, origin)
                        return@awaitEachGesture
                    }
                    isSideways = false
                } else {
                    val travel = up.position - down.position
                    if (duration > SLOPPY_TAP_MAX_MILLIS || travel.getDistance() > size.height * SLOPPY_TAP_MAX_TRAVEL_FRACTION) return@awaitEachGesture
                    isSideways = abs(travel.x) > abs(travel.y)
                }
                launch {
                    withFrameNanos { }
                    if (isSideways) {
                        snapshotFlow { pagerState.isScrollInProgress }.first { !it }
                        if (pagerState.currentPage != page) return@launch
                    }
                    currentOnStep(direction, origin)
                }
            }
        }
    }
}

private const val SLOPPY_TAP_MAX_MILLIS = 250L
private const val SLOPPY_TAP_MAX_TRAVEL_FRACTION = 0.1f
