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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The fling of a song read across the columns, which comes to rest at [snappedScrollTarget] rather than wherever the
 * decay would leave it, so that a row is read from its divider rather than from somewhere in the middle of its first
 * line. The [rows] are written by the layout every time it places them, in the scroll's own coordinates; the first
 * resting offset is the top of the song. With none (a song laid out in a single column) the fling
 * is the ordinary one. They are state, since the buttons that step through the song are
 * shown from them; the layout writes them only when they change.
 *
 * However hard the song is flung, it goes no further than a press of a step button would take it from where the finger
 * let go ([nextStepTarget], [previousStepTarget]): a guitarist reaching for the screen between two strums swipes as
 * often as they tap, and a swipe that skipped a row would have them hunting for their place in the middle of the song.
 * A drag is the finger's own, and still takes the song anywhere.
 */
internal class RowSnapFlingBehavior(
    private val scrollState: ScrollState,
    private val decay: DecayAnimationSpec<Float>,
    private val defaultFling: FlingBehavior,
) : FlingBehavior {

    var rows by mutableStateOf(SongRows())
        private set

    /** Where the fling being animated comes to rest, null while none is: where the reader is headed, before they are there. */
    var flingTarget by mutableStateOf<Int?>(null)
        private set

    /** What of the viewport the song is read through, which the page writes whenever its insets or text size change. */
    var readingWindow by mutableStateOf(ReadingWindow())

    /** Where the reader is in the song, see [keepReaderInPlace]. */
    private var anchor: ReadingAnchor? = null

    /** The viewport the last [rows] were placed in. */
    private var placedViewport: IntSize? = null

    /**
     * Takes the [placedRows] the layout put the song in within a [viewport], in the layout pass that placed them. Where only
     * the viewport's height changed - the metronome panel opening or closing above the page, the short window's title row
     * collapsing - the reader is put back on their place in that same pass, before the scroll places the song: every row is
     * padded to that height, so every divider below the top of the song moves with it, and a scroll left where it was until
     * the change is over has the divider above the row being read slide into view for as long as the change lasts, and the
     * song only catch up after it. A change of the width or of the text size reflows the song, and is left to
     * [keepReaderInPlace].
     */
    fun onRowsPlaced(placedRows: SongRows, viewport: IntSize) {
        val previousViewport = placedViewport
        placedViewport = viewport
        if (placedRows == rows) return
        val currentAnchor = anchor
        val isOnlyHeightChanged = previousViewport != null && previousViewport.width == viewport.width && previousViewport.height != viewport.height
        if (currentAnchor != null && isOnlyHeightChanged && !scrollState.isScrollInProgress) {
            anchoredScrollOffset(currentAnchor, placedRows, viewport.height, Int.MAX_VALUE, readingWindow.top)?.let { target ->
                // Clamped to the extent of the previous layout, which only a reader near the end of the song can be past;
                // the rest of the way is then left to keepReaderInPlace.
                scrollState.dispatchRawDelta((target - scrollState.value).toFloat())
            }
        }
        rows = placedRows
    }

    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        val start = scrollState.value.toFloat()
        val decayTarget = start + decay.calculateTargetValue(0f, initialVelocity)
        val currentRows = rows
        val cappedTarget = oneStepCappedTarget(
            start = start,
            target = decayTarget,
            rows = currentRows,
            viewportHeight = scrollState.viewportSize,
            window = readingWindow,
            maxValue = scrollState.maxValue,
        )
        val target = if (currentRows.restingOffsets.isEmpty()) cappedTarget else snappedScrollTarget(
            start = start,
            target = cappedTarget,
            rows = currentRows,
            viewportHeight = scrollState.viewportSize,
            maxValue = scrollState.maxValue,
        )
        flingTarget = target.roundToInt()
        try {
            return performFlingTo(start, target, decayTarget, initialVelocity)
        } finally {
            flingTarget = null
        }
    }

    private suspend fun ScrollScope.performFlingTo(start: Float, target: Float, decayTarget: Float, initialVelocity: Float): Float {
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
     * Brings the reader back to where they were in the song once it has been laid out again - a window resized, the
     * text pinched or stepped to another size, a section folded, the song rewritten by a transposition - since the
     * scroll keeps its position in pixels, which is somewhere else entirely in the new layout: on the row, or the
     * section, they were reading. Where the reader is ([ReadingAnchor]) is taken whenever the scroll comes to rest,
     * from the stops it came to rest among, never from a position a new layout has already clamped, and never from
     * where a move of this function's own left it, so a pinch through a hundred layouts returns to the row it started
     * in rather than to one it passed on the way.
     *
     * The move waits until the layout has held still for [LAYOUT_SETTLE_MILLIS]: a pinch or a window edge being dragged
     * lays the song out on every frame while its sections glide to their new places, and a scroll following each of
     * those layouts under them flickers. It is animated, like a fling coming to rest, and waits for the end of the
     * scroll to have caught up with the new layout where that is still growing. A scroll of the reader's own, begun
     * before or during the move, is left alone, and is where the reader is once it comes to rest. A viewport that only
     * changes height is followed frame by frame instead (see [onRowsPlaced]), which leaves this nothing to move.
     */
    suspend fun keepReaderInPlace(): Nothing = coroutineScope {
        var isMoving = false
        // Where a move of this function's own left the scroll, which is not somewhere the reader chose.
        var movedTo: Int? = null
        launch {
            snapshotFlow { scrollState.isScrollInProgress }.collect { isScrolling ->
                if (isScrolling || isMoving) return@collect
                if (scrollState.value != movedTo) anchor = readingAnchorOf(scrollState.value, rows, readingWindow.top)
                movedTo = null
            }
        }
        snapshotFlow { rows }.collectLatest { currentRows ->
            if (anchor == null) {
                anchor = readingAnchorOf(scrollState.value, currentRows, readingWindow.top)
                return@collectLatest
            }
            delay(LAYOUT_SETTLE_MILLIS)
            // Read after the wait, since a scroll the reader made during it came to rest on this layout.
            val currentAnchor = anchor ?: return@collectLatest
            if (scrollState.isScrollInProgress) return@collectLatest
            val target = anchoredScrollOffset(currentAnchor, currentRows, scrollState.viewportSize, Int.MAX_VALUE, readingWindow.top) ?: return@collectLatest
            withTimeoutOrNull(LAYOUT_SETTLE_MILLIS * 4) { snapshotFlow { scrollState.maxValue }.first { it >= target } }
            val reachableTarget = target.coerceAtMost(scrollState.maxValue)
            if (reachableTarget == scrollState.value) return@collectLatest
            isMoving = true
            try {
                scrollState.animateScrollTo(reachableTarget)
            } finally {
                isMoving = false
                movedTo = scrollState.value
            }
        }
        awaitCancellation()
    }
}

/**
 * Where the reader is in a song, in terms that outlive a new layout of it: the stop they are past - a row, or a section
 * where the song is not stepped through by rows - named by the section it starts with ([section], null above the first
 * one), and how far past it they are ([offset]).
 *
 * Inside a stop that is taller than the screen that offset is a number of pixels, which is a different line at every
 * text size, so there the reader is also anchored by the [line] they are reading, and the stop only stands in for it
 * where the new layout has no such line.
 */
internal data class ReadingAnchor(val section: Int?, val offset: Int, val line: LineAnchor? = null)

/**
 * The piece of a single column the reader is at (see [SongRows.lineTops]), named by its [section] and its [index] among
 * that section's pieces - both the same in every layout, since a section a single column holds is composed one chunk
 * per line whatever the width and the text size - with how far below its top the reading position is ([offset]) and
 * how tall it was then ([height]), so that the place inside a line that has wrapped differently is kept as a proportion.
 */
internal data class LineAnchor(val section: Int, val index: Int, val offset: Int, val height: Int)

/**
 * Where the reader is at [scroll] among the stops of [rows]: past the lowest one above it, which in columns read top to
 * bottom need not be the last one listed, and at the line that is under [readingTop], where reading starts below the
 * top of the viewport. A reader resting on the stop itself is anchored by the stop alone, so that they are put back on
 * it exactly. Null before the song has been laid out.
 */
internal fun readingAnchorOf(scroll: Int, rows: SongRows, readingTop: Int = 0): ReadingAnchor? {
    if (rows.stepOffsets.isEmpty() || rows.stepSections.size != rows.stepOffsets.size) return null
    val stop = stopAt(scroll, rows.stepOffsets)
    if (stop < 0) return ReadingAnchor(section = null, offset = scroll)
    val stopSection = rows.stepSections[stop]
    val offset = scroll - rows.stepOffsets[stop]
    val anchor = ReadingAnchor(section = stopSection, offset = offset.coerceAtLeast(0))
    if (offset <= POSITION_TOLERANCE || !rows.hasLines) return anchor
    // The rows before the stop hold only earlier sections, so a stop with no line of its own - a row of several
    // columns - finds none here and is anchored as before.
    val at = scroll + readingTop
    var line = -1
    rows.lineTops.forEachIndexed { index, top ->
        if (rows.lineSections[index] >= stopSection && top <= at) line = index
    }
    if (line < 0) return anchor
    val lineSection = rows.lineSections[line]
    return anchor.copy(
        line = LineAnchor(
            section = lineSection,
            index = (0 until line).count { rows.lineSections[it] == lineSection },
            offset = at - rows.lineTops[line],
            height = rows.lineBottoms[line] - rows.lineTops[line],
        ),
    )
}

/**
 * The scroll position that puts the reader back where [anchor] says they were among [rows] laid out anew: on the same
 * line under [readingTop], as far into it as they were in proportion to its height, where the new layout still pages
 * through that line in a single column; otherwise past the stop that now holds the section theirs started with, as far
 * as they were past theirs. Never so far into a row that its content ends above the bottom of the viewport, and above
 * the first stop no further down than it. Null before the song has been laid out.
 */
internal fun anchoredScrollOffset(anchor: ReadingAnchor, rows: SongRows, viewportHeight: Int, maxValue: Int, readingTop: Int = 0): Int? {
    if (rows.stepOffsets.isEmpty() || rows.stepSections.size != rows.stepOffsets.size) return null
    val section = anchor.section ?: return anchor.offset.coerceIn(0, rows.stepOffsets.min().coerceAtLeast(0)).coerceIn(0, maxValue)
    val line = anchor.line
    val lineIndex = if (line != null && rows.hasLines) {
        // A section folded since lists one piece where it listed many, and one now in a row of several lists none.
        var seen = 0
        rows.lineSections.indices.firstOrNull { index -> rows.lineSections[index] == line.section && seen++ == line.index }
    } else {
        null
    }
    if (line == null || lineIndex == null) {
        val stop = stopHolding(section, rows)
        return (rows.stepOffsets[stop] + anchor.offset).coerceAtMost(lastFreeOffset(stop, rows, viewportHeight)).coerceIn(0, maxValue)
    }
    val top = rows.lineTops[lineIndex]
    val height = rows.lineBottoms[lineIndex] - rows.lineTops[lineIndex]
    // Rounded down, towards what has been read. A reader in the gap after the line keeps their distance from it in
    // pixels, since a gap does not grow with the text.
    val mapped = when {
        line.offset >= line.height -> height + (line.offset - line.height)
        else -> (line.offset.toLong() * height / line.height).toInt()
    }
    // The line may now be in a row that starts with another section, so the row is looked up by the line's own.
    val stop = stopHolding(line.section, rows)
    return (top + mapped - readingTop).coerceAtMost(lastFreeOffset(stop, rows, viewportHeight)).coerceIn(0, maxValue)
}

/** The index of the stop of [rows] that holds [section]: the one starting with the latest section not after it. */
private fun stopHolding(section: Int, rows: SongRows): Int {
    var stop = 0
    rows.stepSections.forEachIndexed { index, stopSection ->
        if (stopSection <= section && stopSection > rows.stepSections[stop]) stop = index
    }
    return stop
}

/** The furthest a reader in the [stop] of [rows] may be scrolled, which in a row is where its content meets the bottom of the viewport. */
private fun lastFreeOffset(stop: Int, rows: SongRows, viewportHeight: Int): Int {
    val stepOffset = rows.stepOffsets[stop]
    val bottom = if (rows.isSteppedByRow) rows.bottoms.getOrNull(stop) else null
    return bottom?.let { it - viewportHeight }?.coerceAtLeast(stepOffset) ?: Int.MAX_VALUE
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
private const val LAYOUT_SETTLE_MILLIS = 250L
