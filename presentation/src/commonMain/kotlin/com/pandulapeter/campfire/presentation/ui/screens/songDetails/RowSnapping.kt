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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sign

/**
 * The rows of a song read across the columns, in the coordinates of whatever scrolls it: where a scroll comes to rest
 * on each ([restingOffsets], ascending - the bottom edge of the divider above the row, so that the divider itself is
 * just out of view, or the row's own top where it has no divider) and where each one's content ends ([bottoms], one per
 * resting offset, above whatever empty space follows it). None at all where the song is not read in rows.
 *
 * [stepOffsets] are where Page Up / Page Down and the buttons at the end of the screen step to, ascending, the top of the
 * song not among them: the resting offsets of a song that scrolls in rows, and just above every section of any other, whose
 * header is then the first thing stepped past ([isSteppedByRow] telling the two apart). None before the song has been
 * laid out.
 */
internal data class SongRows(
    val restingOffsets: List<Int> = emptyList(),
    val bottoms: List<Int> = emptyList(),
    val stepOffsets: List<Int> = emptyList(),
    val isSteppedByRow: Boolean = false,
) {
    fun offsetBy(offset: Int) = copy(
        restingOffsets = restingOffsets.map { it + offset },
        bottoms = bottoms.map { it + offset },
        stepOffsets = stepOffsets.map { it + offset },
    )
}

/**
 * Where a fling of the song that set off from [start] and would come to rest at [target] stops instead, when the song
 * is read across the columns: at a scroll position that puts one of the [rows] right under the top of the viewport,
 * or at either end of the song.
 *
 * A row whose content is taller than the viewport cannot be read from its resting offset alone, so once the reader is
 * in it, anywhere from there down to where its content's bottom meets the bottom of the viewport is left where the
 * fling takes it. Snapping then only decides where the reader lands between that and the next row, which is the
 * stretch where the row below is already coming into view. A fling that comes into such a row from another one still
 * lands on its top, since that is where it is read from. Only the content counts, never the empty space a row is
 * followed by to keep the next one out of view: that is not something to read.
 */
internal fun snappedScrollTarget(
    start: Float,
    target: Float,
    rows: SongRows,
    viewportHeight: Int,
    maxValue: Int,
): Float {
    val rowOffsets = rows.restingOffsets.map { it.coerceIn(0, maxValue) }
    val points = (listOf(0, maxValue) + rowOffsets).distinct().sorted()
    val clampedTarget = target.coerceIn(0f, maxValue.toFloat())
    val next = points.indexOfFirst { it >= clampedTarget }
    if (next <= 0) return points.first().toFloat()
    val rowStart = points[next - 1].toFloat()
    val nextPoint = points[next].toFloat()
    // The stretch above the first row is the header's, which is read as a whole with the first row coming into view.
    val bottom = rowOffsets.indexOf(points[next - 1]).takeIf { it >= 0 }?.let { rows.bottoms.getOrNull(it) }?.toFloat() ?: nextPoint
    val lastFree = (bottom - viewportHeight).coerceIn(rowStart, nextPoint)
    val isStartInRow = start >= rowStart - POSITION_TOLERANCE && start < nextPoint - POSITION_TOLERANCE
    return when {
        clampedTarget <= lastFree -> if (isStartInRow) clampedTarget else rowStart
        clampedTarget - lastFree < nextPoint - clampedTarget -> if (isStartInRow) lastFree else rowStart
        else -> nextPoint
    }
}

/**
 * The scroll position that puts the stop after the one at [scroll] - a row, or a section of a single column - under the
 * top of the viewport, or null where there is none left to step to: the last one is on screen, or the song cannot be
 * scrolled far enough for another one.
 */
internal fun nextStepOffset(scroll: Int, stepOffsets: List<Int>, maxValue: Int): Int? {
    // Queried on every scroll update. Sections in different columns need not arrive in vertical order, but finding
    // the nearest stop needs neither a sorted copy nor a list of clamped offsets.
    var next: Int? = null
    for (offset in stepOffsets) {
        val candidate = offset.coerceIn(0, maxValue)
        if (candidate > scroll + POSITION_TOLERANCE && (next == null || candidate < next)) next = candidate
    }
    return next
}

/**
 * The scroll position that puts the stop the one at [scroll] comes after under the top of the viewport - or the stop at
 * [scroll] itself, where the reader is past its top - and null only at the very top of the song. The header above the
 * first stop counts as one of its own, rested on at the top of the song, since it is scrolled away like one.
 */
internal fun previousStepOffset(scroll: Int, stepOffsets: List<Int>, maxValue: Int): Int? {
    var previous: Int? = if (scroll > POSITION_TOLERANCE) 0 else null
    for (offset in stepOffsets) {
        val candidate = offset.coerceIn(0, maxValue)
        if (candidate < scroll - POSITION_TOLERANCE && (previous == null || candidate > previous)) previous = candidate
    }
    return previous
}

/**
 * The fling of a song read across the columns, which comes to rest at [snappedScrollTarget] rather than wherever the
 * decay would leave it, so that a row is read from its divider rather than from somewhere in the middle of its first
 * line. The [rows] are written by the layout every time it places them, in the scroll's own coordinates; the first
 * resting offset is the one above the song, below its header. With none (a song laid out in a single column, or read
 * column by column) the fling is the ordinary one. They are state, since the buttons that step through the song are
 * shown from them; the layout writes them only when they change.
 */
internal class RowSnapFlingBehavior(
    private val scrollState: ScrollState,
    private val decay: DecayAnimationSpec<Float>,
    private val defaultFling: FlingBehavior,
) : FlingBehavior {

    var rows by mutableStateOf(SongRows())

    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        val start = scrollState.value.toFloat()
        val decayTarget = start + decay.calculateTargetValue(0f, initialVelocity)
        val currentRows = rows
        val target = if (currentRows.restingOffsets.isEmpty()) decayTarget else snappedScrollTarget(
            start = start,
            target = decayTarget,
            rows = currentRows,
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
        // The scroll keeps the fraction of a pixel a drag left it at, so the animation above, which is counted from the
        // whole pixel, can end a pixel away from the target - and a pixel of the divider the song was to rest under
        // shows. The scroll rounds its position to the pixel nearest the one asked for, so asking for the whole
        // pixels still missing lands exactly on it.
        val remainder = target - scrollState.value
        if (remainder != 0f) scrollBy(remainder)
        return 0f
    }

    /**
     * Brings the song to rest where a fling that set off from where it is with no velocity would, which is how it is
     * put back on a divider after the rows moved under it: a fold toggled opens or closes a section, and every divider
     * after it moves while the scroll stays where it was.
     */
    suspend fun settle() = scrollState.scroll { performFling(0f) }
}

/**
 * Steps the song scrolled by [scrollState] from one stop to the next or the previous one, through the [SongRows.stepOffsets]
 * its [flingBehavior] is handed with the rows: what the buttons at the end of the screen and Page Up / Page Down do. The
 * two offsets are derived state, so that whatever shows a way to step is only told when there starts or stops being one.
 */
internal class SongStepper(
    private val scrollState: ScrollState,
    private val flingBehavior: RowSnapFlingBehavior,
) {
    private val previous = derivedStateOf {
        val stepOffsets = flingBehavior.rows.stepOffsets
        if (stepOffsets.isEmpty()) null else previousStepOffset(scrollState.value, stepOffsets, scrollState.maxValue)
    }
    private val next = derivedStateOf { nextStepOffset(scrollState.value, flingBehavior.rows.stepOffsets, scrollState.maxValue) }

    val isSteppedByRow get() = flingBehavior.rows.isSteppedByRow
    // The target changes as each section is passed, but the screen only needs to know whether a target exists.
    val canStepBack by derivedStateOf { previous.value != null }
    val canStepForward by derivedStateOf { next.value != null }

    /** Scrolls to the previous stop where [direction] is negative and to the next one otherwise, if there is one. */
    suspend fun step(direction: Int) {
        val target = (if (direction < 0) previous.value else next.value) ?: return
        scrollState.animateScrollTo(target)
    }
}

/** The [RowSnapFlingBehavior] of a song page scrolled by [scrollState]. */
@Composable
internal fun rememberRowSnapFlingBehavior(scrollState: ScrollState): RowSnapFlingBehavior {
    val decay = rememberSplineBasedDecay<Float>()
    val defaultFling = ScrollableDefaults.flingBehavior()
    return remember(scrollState, decay, defaultFling) { RowSnapFlingBehavior(scrollState = scrollState, decay = decay, defaultFling = defaultFling) }
}

/** How far apart two scroll positions may be and still count as one, since an animated scroll ends on a rounded pixel. */
private const val POSITION_TOLERANCE = 1f
private const val MIN_ANGULAR_FREQUENCY = 12f
private const val MAX_ANGULAR_FREQUENCY = 40f
