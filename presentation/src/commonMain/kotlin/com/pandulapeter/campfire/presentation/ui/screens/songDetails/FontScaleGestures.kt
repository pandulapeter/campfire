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
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.pointerInput
import com.pandulapeter.campfire.presentation.ui.platform.verticalWheelNotches
import kotlin.math.pow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Lets the user change the text size of the song details screen with a pinch (touch) or with Ctrl / Cmd + scroll
 * wheel (pointer). Both are handled in the initial pass and consumed, so that the scrolling content and the pager
 * underneath never react to them; single finger gestures and plain scrolling are left alone.
 *
 * The pinch is deliberately damped ([PINCH_SENSITIVITY]): the text has to be readable while it is being resized,
 * so precision matters more than speed.
 *
 * A new scale is reported at most once per frame, with the latest value the gesture arrived at: pointer events come
 * faster than frames, and every scale reported lays the whole song out again, which on a long song is more work than
 * a frame has room for. Only a frame that has something to report is asked for, so the screen is not kept drawing
 * while nobody touches it.
 *
 * @param fontScale Returns the current scale, so that the gesture can build on it.
 * @param onFontScaleChanged Called with the new (unclamped) scale as the gesture progresses.
 */
internal fun Modifier.fontScaleGestures(
    fontScale: () -> Float,
    onFontScaleChanged: (Float) -> Unit,
) = pointerInput(Unit) {
    coroutineScope {
        // The scale the gesture has arrived at but not yet reported. The gestures build on it rather than on
        // [fontScale], which only changes once it has been reported, so that the events of one frame add up instead
        // of each starting again from the same value.
        var pendingFontScale: Float? = null
        val frameRequests = Channel<Unit>(Channel.CONFLATED)
        val changeFontScale = { value: Float ->
            pendingFontScale = value
            frameRequests.trySend(Unit)
        }
        launch {
            while (true) {
                frameRequests.receive()
                withFrameNanos { }
                pendingFontScale?.let {
                    pendingFontScale = null
                    onFontScaleChanged(it)
                }
            }
        }
        launch {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                var pointerCount = 0
                var pinchStart: PinchStart? = null
                var isPinching = false
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val pressedPointerCount = event.changes.count { it.pressed }
                    if (pressedPointerCount != pointerCount) {
                        // A finger was added or lifted: the distances are no longer comparable, start a new baseline.
                        pointerCount = pressedPointerCount
                        pinchStart = null
                    }
                    if (pressedPointerCount >= 2) {
                        isPinching = true
                        val spread = event.calculateCentroidSize(useCurrent = true)
                        if (spread > 0f) {
                            val start = pinchStart
                            if (start == null) {
                                pinchStart = PinchStart(spread = spread, fontScale = pendingFontScale ?: fontScale())
                            } else {
                                changeFontScale(start.fontScale * (spread / start.spread).pow(PINCH_SENSITIVITY))
                            }
                        }
                    }
                    // Once a pinch has started, the rest of the gesture belongs to it, even after fingers are lifted.
                    if (isPinching) event.changes.forEach { it.consume() }
                } while (event.changes.any { it.pressed })
            }
        }
        launch {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.type == PointerEventType.Scroll && (event.keyboardModifiers.isCtrlPressed || event.keyboardModifiers.isMetaPressed)) {
                        // Towards the user (a positive delta) zooms out and away zooms in, like in a browser. Counted in notches,
                        // since the web hands over the browser's own pixels.
                        changeFontScale((pendingFontScale ?: fontScale()) - event.verticalWheelNotches() * SCROLL_SENSITIVITY)
                        event.changes.forEach { it.consume() }
                    }
                }
            }
        }
    }
}

/** The average distance of the fingers from their centroid and the font scale when the pinch began. */
private data class PinchStart(
    val spread: Float,
    val fontScale: Float,
)

private const val PINCH_SENSITIVITY = 0.4f // Exponent applied to the spread ratio of the fingers.
private const val SCROLL_SENSITIVITY = 0.05f // Font scale change per notch of the scroll wheel.
