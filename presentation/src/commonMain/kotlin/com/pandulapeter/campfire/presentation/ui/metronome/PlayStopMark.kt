/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.metronome

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.floor

/**
 * The play and stop mark of both metronomes' buttons, morphing from one into the other: the triangle's three corners
 * (one of them doubled) travel to the square's four, so the change reads as one shape becoming another rather than as
 * two icons swapping.
 */
@Composable
internal fun PlayStopMark(
    modifier: Modifier = Modifier,
    isPlaying: Boolean,
    size: Dp = 24.dp,
    color: Color = LocalContentColor.current,
) {
    // The mark turns the way the setlist assignments star does, a quarter turn clockwise whichever way it is going, but
    // easing in rather than leaning back and overshooting: a press that starts or stops the click is answered at once
    // and ends on the beat, where a bounce would blur when it took effect. The morph runs over the same turn. A square looks the same a quarter turn back and the triangle a whole turn back, so a turn that starts
    // from rest is first snapped to the angle it would have to start from to end upright, which is what lets stop turn
    // into play clockwise too. A change that arrives mid-turn carries on clockwise from wherever the mark is, to the next
    // angle the new shape rests upright at.
    val progress by animateFloatAsState(if (isPlaying) 1f else 0f, playStopMorphSpec())
    val turn = remember { Animatable(0f) }
    var turnTarget by remember { mutableFloatStateOf(0f) }
    var turnedFor by remember { mutableStateOf(isPlaying) }
    LaunchedEffect(isPlaying) {
        if (turnedFor != isPlaying) {
            turnedFor = isPlaying
            if (turn.value == turnTarget) {
                turnTarget = if (isPlaying) 0f else -PLAY_STOP_TURN
                turn.snapTo(turnTarget)
            }
            turnTarget = if (isPlaying) turnTarget + PLAY_STOP_TURN else (floor(turnTarget / FULL_TURN) + 1) * FULL_TURN
            turn.animateTo(
                targetValue = turnTarget,
                animationSpec = tween(
                    durationMillis = PLAY_STOP_TURN_DURATION,
                    easing = FastOutLinearInEasing,
                ),
            )
        }
    }
    Canvas(modifier = modifier.size(size).graphicsLayer { rotationZ = turn.value }) {
        val unit = this.size.minDimension / 24f
        val path = Path()
        PLAY_CORNERS.indices.forEach { index ->
            val point = lerp(PLAY_CORNERS[index], STOP_CORNERS[index], progress) * unit
            if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
        }
        path.close()
        drawPath(path, color)
    }
}

/**
 * The timing of [PlayStopMark]'s morph: the length of the turn, on an ordinary easing, since easing in as well would leave
 * the shape unchanged until the turn is nearly over.
 */
private fun <T> playStopMorphSpec(): FiniteAnimationSpec<T> = tween(durationMillis = PLAY_STOP_TURN_DURATION)

/** How far [PlayStopMark] turns between its two states, in degrees, and how long that takes in milliseconds. */
private const val PLAY_STOP_TURN = 90f
private const val PLAY_STOP_TURN_DURATION = 250
private const val FULL_TURN = 360f

private fun lerp(start: Offset, stop: Offset, fraction: Float) = Offset(
    x = start.x + (stop.x - start.x) * fraction,
    y = start.y + (stop.y - start.y) * fraction,
)

/** Material's play triangle on its 24-unit grid, the tip doubled so that it can become two corners of the square. */
private val PLAY_CORNERS = listOf(Offset(8f, 5f), Offset(19f, 12f), Offset(19f, 12f), Offset(8f, 19f))
private val STOP_CORNERS = listOf(Offset(6f, 6f), Offset(18f, 6f), Offset(18f, 18f), Offset(6f, 18f))
