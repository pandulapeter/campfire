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

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt

/**
 * The search action's icon, which is the same mark whether the search is closed or open: a magnifier whose lens
 * unwinds while its handle grows backwards into one diagonal of a cross, and a second diagonal that draws itself in
 * behind it. Reversed on the way back, so the mark returns along the path it left by.
 *
 * It is drawn here rather than loaded as a resource because it is an animated vector, and Compose Multiplatform's
 * resources only read the static kind. Everything about it — the geometry in a 24 unit viewport, the stroke width,
 * the offsets of the three stages and their easings — is the `avd_search_to_close.xml` the icon was designed as,
 * transcribed. Each part therefore keeps its own slice of one shared timeline instead of being animated on its own:
 * the stages deliberately overlap, and three springs of their own would drift apart.
 *
 * The stages are kept in the milliseconds they were authored in ([AUTHORED_DURATION]) and the whole timeline is
 * *played* over [PLAYBACK_DURATION], which is shorter: what the drawing carries is the choreography — which stage
 * waits for which, and by how much — and that is a set of proportions rather than a length. A toolbar icon under a
 * finger has to be done well before an icon being designed in isolation does.
 *
 * @param isClose Which end of the animation to be at. The rest of the timeline is played to get there.
 * @param backProgress How far a back gesture that would close the search has been dragged. While it is above zero
 *   the mark is held that far back along its timeline, the cross already turning back into the magnifier, so the
 *   gesture previews what letting go of it does. Whenever it drops back to zero the mark carries on from wherever
 *   the gesture left it — on to the magnifier if the search closed, back to the cross if the gesture was cancelled —
 *   rather than jumping to the end it was last animated to first.
 */
@Composable
internal fun SearchToCloseIcon(
    modifier: Modifier = Modifier,
    isClose: Boolean,
    backProgress: Float = 0f,
    contentDescription: String,
    tint: Color = LocalContentColor.current,
) {
    val timeline = remember { Animatable(if (isClose) 1f else 0f) }
    LaunchedEffect(isClose, backProgress) {
        if (isClose && backProgress > 0f) {
            timeline.snapTo(1f - backProgress)
        } else {
            val target = if (isClose) 1f else 0f
            timeline.animateTo(
                targetValue = target,
                // Linear, since the easing of every stage is its own and is applied to its own slice below. Only as
                // long as the part of the timeline that is left, so that a mark let go of halfway through a gesture
                // finishes at the pace a tap plays the whole of it at.
                animationSpec = tween(durationMillis = (abs(target - timeline.value) * PLAYBACK_DURATION).roundToInt(), easing = LinearEasing),
            )
        }
    }
    // The lens is the one part that has to be measured to be trimmed, so it is built once and measured once. The
    // straight strokes are trimmed by interpolating their two ends, which costs nothing.
    val lens = remember { lensPath() }
    val lensMeasure = remember(lens) { PathMeasure().apply { setPath(lens, forceClosed = false) } }
    val trimmedLens = remember { Path() }
    Canvas(
        modifier = modifier
            .size(ICON_SIZE)
            .semantics { this.contentDescription = contentDescription }
    ) {
        val timeline = timeline.value
        scale(scale = size.minDimension / VIEWPORT_SIZE, pivot = Offset.Zero) {
            val stroke = Stroke(width = STROKE_WIDTH)
            val lensTrimStart = timeline.stage(startMillis = 134, durationMillis = 416, easing = AccelerateDecelerateEasing)
            if (lensTrimStart < 1f) {
                trimmedLens.reset()
                lensMeasure.getSegment(
                    startDistance = lensTrimStart * lensMeasure.length,
                    stopDistance = lensMeasure.length,
                    destination = trimmedLens,
                )
                // What is left of the lens slides up and away from the handle as the handle takes over its corner.
                val lensOffset = timeline.stage(startMillis = 300, durationMillis = 500, easing = FastOutSlowInEasing) * LENS_TRAVEL
                translate(left = lensOffset, top = lensOffset) {
                    drawPath(path = trimmedLens, color = tint, style = stroke)
                }
            }
            // The magnifier's handle, which is the last half of the cross's north west diagonal until it grows back
            // along it. It stops short of the far end, where the handle used to stick out past the lens.
            drawTrimmedLine(
                start = Offset(x = 6f, y = 6f),
                end = Offset(x = 20f, y = 20f),
                trimStart = HANDLE_TRIM_START * (1f - timeline.stage(startMillis = 300, durationMillis = 500, easing = FastOutSlowInEasing)),
                trimEnd = 1f - (1f - HANDLE_TRIM_END) * timeline.stage(startMillis = 300, durationMillis = 500, easing = FastOutSlowInEasing),
                color = tint,
                stroke = stroke,
            )
            drawTrimmedLine(
                start = Offset(x = 18f, y = 6f),
                end = Offset(x = 6f, y = 18f),
                trimStart = 1f - timeline.stage(startMillis = 522, durationMillis = 314, easing = FastOutSlowInEasing),
                trimEnd = 1f,
                color = tint,
                stroke = stroke,
            )
        }
    }
}

/**
 * The fraction one stage of the animation is at, given where the shared timeline is: its own slice of it, run
 * through its own easing and clamped to the stretches before it starts and after it has finished.
 */
private fun Float.stage(startMillis: Int, durationMillis: Int, easing: Easing): Float =
    easing.transform(((this * AUTHORED_DURATION - startMillis) / durationMillis).coerceIn(0f, 1f))

/** A straight stroke drawn between the two fractions of its length it is trimmed to, nothing at all when empty. */
private fun DrawScope.drawTrimmedLine(
    start: Offset,
    end: Offset,
    trimStart: Float,
    trimEnd: Float,
    color: Color,
    stroke: Stroke,
) {
    if (trimStart >= trimEnd) return
    drawLine(
        color = color,
        start = start + (end - start) * trimStart,
        end = start + (end - start) * trimEnd,
        strokeWidth = stroke.width,
    )
}

/**
 * The magnifier's lens: a circle that starts where the handle meets it, so that unwinding it from its start empties
 * it from the corner the handle is about to grow out of rather than from somewhere along the far side.
 */
private fun lensPath() = Path().apply {
    moveTo(13.389f, 13.389f)
    cubicTo(15.537f, 11.241f, 15.537f, 7.759f, 13.389f, 5.611f)
    cubicTo(11.241f, 3.463f, 7.759f, 3.463f, 5.611f, 5.611f)
    cubicTo(3.463f, 7.759f, 3.463f, 11.241f, 5.611f, 13.389f)
    cubicTo(7.759f, 15.537f, 11.241f, 15.537f, 13.389f, 13.389f)
    close()
}

/** Android's `accelerate_decelerate_interpolator`, which the lens unwinds with. */
private val AccelerateDecelerateEasing = Easing { fraction -> (cos((fraction + 1f) * PI.toFloat()) / 2f) + 0.5f }

private val ICON_SIZE = 24.dp
private const val VIEWPORT_SIZE = 24f
private const val STROKE_WIDTH = 1.8f
private const val AUTHORED_DURATION = 836
private const val PLAYBACK_DURATION = 300
private const val LENS_TRAVEL = -6.7f
private const val HANDLE_TRIM_START = 0.48f
private const val HANDLE_TRIM_END = 0.86f
