package com.pandulapeter.campfire.shared.ui.screens.songDetails

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.pow

/**
 * Lets the user change the text size of the song details screen with a pinch (touch) or with Ctrl / Cmd + scroll
 * wheel (pointer). Both are handled in the initial pass and consumed, so that the scrolling content and the pager
 * underneath never react to them; single finger gestures and plain scrolling are left alone.
 *
 * The pinch is deliberately damped ([PINCH_SENSITIVITY]): the text has to be readable while it is being resized,
 * so precision matters more than speed.
 *
 * @param fontScale Returns the current scale, so that the gesture can build on it.
 * @param onFontScaleChanged Called with the new (unclamped) scale as the gesture progresses.
 */
internal fun Modifier.fontScaleGestures(
    fontScale: () -> Float,
    onFontScaleChanged: (Float) -> Unit
) = this
    .pointerInput(Unit) {
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
                            pinchStart = PinchStart(spread = spread, fontScale = fontScale())
                        } else {
                            onFontScaleChanged(start.fontScale * (spread / start.spread).pow(PINCH_SENSITIVITY))
                        }
                    }
                }
                // Once a pinch has started, the rest of the gesture belongs to it, even after fingers are lifted.
                if (isPinching) event.changes.forEach { it.consume() }
            } while (event.changes.any { it.pressed })
        }
    }
    .pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type == PointerEventType.Scroll && (event.keyboardModifiers.isCtrlPressed || event.keyboardModifiers.isMetaPressed)) {
                    val delta = event.changes.fold(0f) { total, change -> total + change.scrollDelta.y }
                    // Scrolling up (negative delta) zooms in, like in a browser.
                    onFontScaleChanged(fontScale() - delta * SCROLL_SENSITIVITY)
                    event.changes.forEach { it.consume() }
                }
            }
        }
    }

/** The average distance of the fingers from their centroid and the font scale when the pinch began. */
private data class PinchStart(
    val spread: Float,
    val fontScale: Float
)

private const val PINCH_SENSITIVITY = 0.4f // Exponent applied to the spread ratio of the fingers.
private const val SCROLL_SENSITIVITY = 0.05f // Font scale change per scroll wheel unit.
