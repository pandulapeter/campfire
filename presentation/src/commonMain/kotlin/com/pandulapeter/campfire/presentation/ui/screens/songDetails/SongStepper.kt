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

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs

/**
 * Steps the song scrolled by [scrollState] from one stop to the next or the previous one, through the [SongRows.stepOffsets]
 * its [flingBehavior] is handed with the rows, paging through a stop that does not fit the screen on the way
 * ([nextStepTarget], [previousStepTarget]): what the buttons at the end of the screen and Page Up / Page Down do. The two
 * targets are derived state, so that whatever shows a way to step is only told when there starts or stops being one.
 */
internal class SongStepper(
    private val scrollState: ScrollState,
    private val flingBehavior: RowSnapFlingBehavior,
) {
    private val previous = derivedStateOf {
        previousStepTarget(scrollState.value, flingBehavior.rows, scrollState.viewportSize, flingBehavior.readingWindow, scrollState.maxValue)
    }
    private val next = derivedStateOf {
        nextStepTarget(scrollState.value, flingBehavior.rows, scrollState.viewportSize, flingBehavior.readingWindow, scrollState.maxValue)
    }

    val isSteppedByRow get() = flingBehavior.rows.isSteppedByRow
    // The target changes with every pixel scrolled, but the screen only needs to know whether a target exists, and
    // whether it is a stop or a page of the one being read.
    val canStepBack by derivedStateOf { previous.value != null }
    val canStepForward by derivedStateOf { next.value != null }
    val isPagingBack by derivedStateOf { previous.value?.let { !isStepStop(it, flingBehavior.rows, scrollState.maxValue) } == true }
    val isPagingForward by derivedStateOf { next.value?.let { !isStepStop(it, flingBehavior.rows, scrollState.maxValue) } == true }

    // Only a new layout changes the stops, so the list is built once per layout rather than once per pixel scrolled.
    private val stops = derivedStateOf { reachableStops(flingBehavior.rows, scrollState.maxValue, flingBehavior.readingWindow.lineHeight) }
    val stopCount by derivedStateOf { stops.value.size }
    // Changes with every pixel scrolled, so it is only to be read where that invalidates nothing but drawing.
    val stopProgress get() = stopProgress(scrollState.value, stops.value)

    // Where the step being animated is headed, which the next press is counted from: the step is slow enough to be
    // followed, and a pedal pressed twice in quick succession means two steps from where the song was, not one step and
    // a bit from wherever the first had got to. Snapshot state, since it is also where the reader is headed (see
    // [headedOffset]).
    private var stepTarget by mutableStateOf<Int?>(null)

    /** Whether the song is being scrolled by a step rather than by a finger or a fling. */
    val isStepping get() = stepTarget != null

    /** Where the next step is counted from: where the one being animated is headed, or where the song is. */
    val origin get() = stepTarget ?: scrollState.value

    /**
     * Where the reader is headed: where a step or a fling being animated comes to rest, or where the song is once it is
     * at rest. Null while a finger is still dragging it, or something else moves it, which says nothing yet about where
     * it will be read.
     */
    val headedOffset get() = stepTarget ?: flingBehavior.flingTarget ?: scrollState.value.takeUnless { scrollState.isScrollInProgress }

    /** Whether there is anything to step to in [direction] from [from], or from [origin] where it is null. */
    fun canStep(direction: Int, from: Int? = null) = when {
        from != null -> stepTargetFrom(from, direction) != null
        direction < 0 -> canStepBack
        else -> canStepForward
    }

    /**
     * Scrolls to the previous stop where [direction] is negative and to the next one otherwise, if there is one, counted
     * from [from] - where a gesture the step stands in for began - or from [origin] where it is null.
     */
    suspend fun step(direction: Int, from: Int? = null) {
        val target = stepTargetFrom(from ?: origin, direction) ?: return
        stepTarget = target
        try {
            // Slow and even, for a reader whose eyes are on the song while it moves: a screen's worth takes as long as
            // STEP_DURATION_PER_SCREEN, and no step is quicker than MIN_STEP_DURATION, so a short one is still seen to move.
            val distance = abs(target - scrollState.value)
            val viewport = scrollState.viewportSize
            val duration = if (viewport > 0) (STEP_DURATION_PER_SCREEN * distance / viewport).coerceIn(MIN_STEP_DURATION, STEP_DURATION_PER_SCREEN) else MIN_STEP_DURATION
            scrollState.animateScrollTo(target, tween(durationMillis = duration, easing = FastOutSlowInEasing))
        } finally {
            if (stepTarget == target) stepTarget = null
        }
    }

    private fun stepTargetFrom(from: Int, direction: Int): Int? {
        val rows = flingBehavior.rows
        val viewport = scrollState.viewportSize
        val window = flingBehavior.readingWindow
        return if (direction < 0) previousStepTarget(from, rows, viewport, window, scrollState.maxValue) else nextStepTarget(from, rows, viewport, window, scrollState.maxValue)
    }
}

private const val STEP_DURATION_PER_SCREEN = 700
private const val MIN_STEP_DURATION = 400
