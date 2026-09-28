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

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.core.spring
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sign

/**
 * Where a fling of the song that would come to rest at [target] stops instead, when the song is read across the
 * columns: at a scroll position that puts one of the row dividers ([dividerOffsets], ascending) at the top of the
 * viewport, or at either end of the song.
 *
 * A row that is taller than the viewport cannot be read from one of those positions alone, so anywhere from its own
 * divider down to where its bottom meets the bottom of the viewport is left where the fling takes it: snapping only
 * decides where the reader lands between that and the next divider, which is the stretch where the row below is
 * already coming into view.
 */
internal fun snappedScrollTarget(
    target: Float,
    dividerOffsets: List<Int>,
    viewportHeight: Int,
    maxValue: Int,
): Float {
    val points = (listOf(0, maxValue) + dividerOffsets.map { it.coerceIn(0, maxValue) }).distinct().sorted()
    val clampedTarget = target.coerceIn(0f, maxValue.toFloat())
    val next = points.indexOfFirst { it >= clampedTarget }
    if (next <= 0) return points.first().toFloat()
    val nextPoint = points[next].toFloat()
    val lastFree = max(points[next - 1], points[next] - viewportHeight).toFloat()
    return when {
        clampedTarget <= lastFree -> clampedTarget
        clampedTarget - lastFree < nextPoint - clampedTarget -> lastFree
        else -> nextPoint
    }
}

/**
 * The fling of a song read across the columns, which comes to rest at [snappedScrollTarget] rather than wherever the
 * decay would leave it, so that a row is read from its divider rather than from somewhere in the middle of its first
 * line. [dividerOffsets] are written by the layout every time it places the rows, in the scroll's own coordinates, and
 * are not state, since nothing is drawn from them; with none (a song laid out in one row, or read column by column)
 * the fling is the ordinary one.
 */
internal class RowSnapFlingBehavior(
    private val scrollState: ScrollState,
    private val decay: DecayAnimationSpec<Float>,
    private val defaultFling: FlingBehavior,
) : FlingBehavior {

    var dividerOffsets = emptyList<Int>()

    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        val start = scrollState.value.toFloat()
        val decayTarget = start + decay.calculateTargetValue(0f, initialVelocity)
        val target = if (dividerOffsets.isEmpty()) decayTarget else snappedScrollTarget(
            target = decayTarget,
            dividerOffsets = dividerOffsets,
            viewportHeight = scrollState.viewportSize,
            maxValue = scrollState.maxValue,
        )
        if (target == decayTarget) return with(defaultFling) { performFling(initialVelocity) }
        val distance = target - start
        if (distance == 0f) return 0f
        // A critically damped spring that sets off at exactly its angular frequency times the distance comes to rest
        // along a plain exponential curve, which is what a decaying fling looks like. So the stiffness is picked from
        // the velocity the finger left with, within bounds: a slow release still settles in a moment, and a fast one
        // towards a divider close by does not overshoot it.
        val isTowardsTarget = initialVelocity.sign == distance.sign
        val angularFrequency = if (isTowardsTarget) (abs(initialVelocity) / abs(distance)).coerceIn(MIN_ANGULAR_FREQUENCY, MAX_ANGULAR_FREQUENCY) else MIN_ANGULAR_FREQUENCY
        val velocity = if (isTowardsTarget) initialVelocity.coerceIn(-angularFrequency * abs(distance), angularFrequency * abs(distance)) else initialVelocity
        var scrolled = 0f
        AnimationState(initialValue = 0f, initialVelocity = velocity).animateTo(
            targetValue = distance,
            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = angularFrequency * angularFrequency),
            sequentialAnimation = true,
        ) {
            val delta = value - scrolled
            val consumed = scrollBy(delta)
            scrolled += consumed
            // The end of the song was reached before the divider was, so there is nothing left to move.
            if (abs(delta - consumed) > 0.5f) cancelAnimation()
        }
        return 0f
    }

    /**
     * Brings the song to rest where a fling that set off from where it is with no velocity would, which is how it is
     * put back on a divider after the rows moved under it: a fold toggled opens or closes a section, and every divider
     * after it moves while the scroll stays where it was.
     */
    suspend fun settle() = scrollState.scroll { performFling(0f) }
}

/** The [RowSnapFlingBehavior] of a song page scrolled by [scrollState]. */
@Composable
internal fun rememberRowSnapFlingBehavior(scrollState: ScrollState): RowSnapFlingBehavior {
    val decay = rememberSplineBasedDecay<Float>()
    val defaultFling = ScrollableDefaults.flingBehavior()
    return remember(scrollState, decay, defaultFling) { RowSnapFlingBehavior(scrollState = scrollState, decay = decay, defaultFling = defaultFling) }
}

private const val MIN_ANGULAR_FREQUENCY = 12f
private const val MAX_ANGULAR_FREQUENCY = 40f
