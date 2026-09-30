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

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.floor

/**
 * A dot for every stop of the song being read - its rows, or its sections where it is read in a single column - with the
 * reader's place among them marked, drawn in the column between the two step buttons, which step between the same stops.
 * The mark follows the scroll itself rather than the stop it is in, so it slides from one dot to the next as the song
 * moves - under a finger, a fling or a step alike - and moves on through a row taller than the screen as it is read. It
 * answers whichever song is current, like the buttons, so paging to a song of more or fewer stops grows or shrinks the
 * column of dots into its new length rather than redrawing it. A song
 * with fewer than two stops, which includes every song that does not scroll, has nothing to show and fades it away.
 *
 * More stops than the column has room for are scrolled through with the mark kept near the middle, and the dots fade
 * and shrink towards whichever end has more beyond it, as far as there is more, the way the edges of a scrolled list fade.
 *
 * Everything moving is read while drawing, so neither the scroll nor a count animating recomposes anything. It fades by the
 * colors it draws with rather than through a layer, since the web build does not redraw a layer whose alpha alone changed.
 */
@Composable
internal fun StepProgressIndicator(
    modifier: Modifier = Modifier,
    stepper: SongStepper?,
) {
    val stopCount = stepper?.stopCount ?: 0
    val isShown = stopCount >= MIN_STOP_COUNT
    val visibility by animateFloatAsState(
        targetValue = if (isShown) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
    )
    val count = remember { Animatable(stopCount.toFloat()) }
    val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    // A column fading in shows the song it arrives with as it is, rather than growing out of the one it faded out on.
    LaunchedEffect(stopCount, isShown) {
        if (!isShown) return@LaunchedEffect
        if (visibility == 0f) count.snapTo(stopCount.toFloat()) else count.animateTo(stopCount.toFloat(), spatialSpec)
    }
    val dotColor = MaterialTheme.colorScheme.outlineVariant
    val selectedColor = MaterialTheme.colorScheme.primary
    // The dots scrolled past either end of the column are drawn only as far as its edge, which is where they fade out to.
    Canvas(modifier = modifier.clipToBounds()) {
        val alpha = visibility
        if (alpha == 0f) return@Canvas
        val pitch = DOT_PITCH.toPx()
        val available = size.height
        val animatedCount = count.value
        val progress = stepper?.stopProgress ?: 0f
        val animatedSelected = progress.coerceIn(0f, (animatedCount - 1f).coerceAtLeast(0f))
        // The header is no stop of its own, so while it is being read the mark is on none of the dots: it fades in as the
        // header is scrolled away and the first stop comes up to the top.
        val headerFactor = (1f + progress).coerceIn(0f, 1f)
        val contentHeight = animatedCount * pitch
        val offset = stepProgressOffset(contentHeight = contentHeight, availableHeight = available, markCenter = (animatedSelected + 0.5f) * pitch)
        val hiddenAbove = -offset.coerceAtMost(0f)
        val hiddenBelow = (contentHeight + offset - available).coerceAtLeast(0f)
        val fadeLength = (FADE_DOT_COUNT * pitch).coerceAtMost(available / 3f)
        fun edgeFactor(y: Float): Float {
            if (fadeLength <= 0f) return 1f
            val top = 1f - (hiddenAbove / fadeLength).coerceIn(0f, 1f) * (1f - (y / fadeLength).coerceIn(0f, 1f))
            val bottom = 1f - (hiddenBelow / fadeLength).coerceIn(0f, 1f) * (1f - ((available - y) / fadeLength).coerceIn(0f, 1f))
            return top * bottom
        }
        val x = size.width / 2f
        val dotRadius = DOT_RADIUS.toPx()
        val first = floor(-offset / pitch - 1f).toInt().coerceAtLeast(0)
        val last = ceil((available - offset) / pitch).toInt().coerceAtMost(ceil(animatedCount).toInt() - 1)
        for (index in first..last) {
            // A dot being added or taken away grows out of nothing or shrinks into it, at the end of the column.
            val presence = (animatedCount - index).coerceIn(0f, 1f)
            val y = (index + 0.5f) * pitch + offset
            val factor = edgeFactor(y) * presence
            if (factor <= 0f) continue
            drawCircle(
                color = dotColor,
                radius = dotRadius * (MIN_EDGE_SCALE + (1f - MIN_EDGE_SCALE) * factor),
                center = Offset(x, y),
                alpha = alpha * factor,
            )
        }
        val markY = (animatedSelected + 0.5f) * pitch + offset
        val markFactor = edgeFactor(markY) * headerFactor
        drawCircle(
            color = selectedColor,
            radius = SELECTED_DOT_RADIUS.toPx() * (MIN_EDGE_SCALE + (1f - MIN_EDGE_SCALE) * markFactor),
            center = Offset(x, markY),
            alpha = alpha * markFactor,
        )
    }
}

/**
 * How far down the column of dots [contentHeight] tall is drawn in the [availableHeight] it has: centered where it fits,
 * and otherwise scrolled - by a negative offset - so that the mark at [markCenter] is in the middle, as far as the ends
 * of the column let it be. The two agree where the column fits exactly, so a column growing past its room carries on
 * smoothly into being scrolled.
 */
internal fun stepProgressOffset(contentHeight: Float, availableHeight: Float, markCenter: Float) = if (contentHeight <= availableHeight) {
    (availableHeight - contentHeight) / 2f
} else {
    (availableHeight / 2f - markCenter).coerceIn(availableHeight - contentHeight, 0f)
}

private const val MIN_STOP_COUNT = 2
private const val FADE_DOT_COUNT = 3f
private const val MIN_EDGE_SCALE = 0.4f
private val DOT_PITCH = 12.dp
private val DOT_RADIUS = 3.dp
private val SELECTED_DOT_RADIUS = 4.dp
