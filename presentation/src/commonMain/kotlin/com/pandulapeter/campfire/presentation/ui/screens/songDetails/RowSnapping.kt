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

import kotlinx.coroutines.flow.first

/**
 * The rows of a song read across the columns, in the coordinates of whatever scrolls it: where a scroll comes to rest
 * on each ([restingOffsets], ascending - the bottom edge of the divider above the row, so that the divider itself is
 * just out of view, or the row's own top where it has no divider) and where each one's content ends ([bottoms], one per
 * resting offset, above whatever empty space follows it). None at all where the song is not read in rows.
 *
 * [stepOffsets] are where Page Up / Page Down and the buttons at the end of the screen step to, ascending: the resting
 * offsets of a song that scrolls in rows, and just above every section of any other ([isSteppedByRow] telling the two
 * apart), the first one at the top of the song either way, so that a song opens on its first stop. None before the song
 * has been laid out. Each stop is also named by the index of the section it starts with ([stepSections], one per stop), which is
 * what it is still called once a new layout has flowed the sections into different rows or columns (see [ReadingAnchor]).
 *
 * [lineTops] are where the pieces a single column is made of start - every section, and within one the chunks it may be
 * cut into, which start between two of its lines (see [sectionChunkStarts]) - so that a step that pages through what
 * does not fit the screen can bring a line to the top of it rather than half of one. [lineBottoms] are where each of
 * those pieces ends and [lineSections] the section each belongs to, which together name a line in terms another layout
 * of the song still has (see [LineAnchor]).
 *
 * [timingSections] are the sections that change the tempo or the time signature the song is played in from there on,
 * ascending, each of which starts a stop of its own wherever the song is stepped by rows (see [timingIndexAt]). A change
 * written before the song's first line is reported at section 0 and starts no stop, since it is in force from the first.
 */
internal data class SongRows(
    val restingOffsets: List<Int> = emptyList(),
    val bottoms: List<Int> = emptyList(),
    val stepOffsets: List<Int> = emptyList(),
    val stepSections: List<Int> = emptyList(),
    val isSteppedByRow: Boolean = false,
    val lineTops: List<Int> = emptyList(),
    val lineBottoms: List<Int> = emptyList(),
    val lineSections: List<Int> = emptyList(),
    val timingSections: List<Int> = emptyList(),
) {
    /**
     * These rows in the coordinates of a scroll that holds [topPadding] above them: everything that much further down,
     * except a stop at or above their top, which is rested on at the top of the scroll. The padding is read with the first
     * row or section rather than scrolled past on the way to it, so the song opens on its first stop.
     */
    fun belowPadding(topPadding: Int) = copy(
        restingOffsets = restingOffsets.map { if (it <= 0) 0 else it + topPadding },
        bottoms = bottoms.map { it + topPadding },
        stepOffsets = stepOffsets.map { if (it <= 0) 0 else it + topPadding },
        lineTops = lineTops.map { it + topPadding },
        lineBottoms = lineBottoms.map { it + topPadding },
    )

    /**
     * How far a scroll at [scroll] has taken the song past the top of the row it is in - the distance from the last
     * resting offset at or above it - which is how far the top edge's fade has anything of that row to fade. A row
     * rested on starts right under the top of the viewport, and fading it there would cover its first line; what is
     * above a row is the empty space after the one before it, which has nothing in it to fade. Where the song is not
     * read in rows, it is the scroll itself.
     */
    fun scrolledIntoRow(scroll: Int) = scroll - (restingOffsets.lastOrNull { it <= scroll } ?: 0)

    /** Whether the three lists that describe the pieces of a single column describe the same pieces. */
    val hasLines get() = lineTops.isNotEmpty() && lineBottoms.size == lineTops.size && lineSections.size == lineTops.size
}

/**
 * Which change of tempo or time signature ([SongRows.timingSections]) a scroll at [offset] reads the song under: the
 * last one at or before the section the stop at or above it starts with, -1 before the first, which is the song's
 * opening. A scroll at the end of the song, [maxValue], is at its last stop, which the end of a song stepped by its
 * sections may never bring to the top of the screen.
 */
internal fun timingIndexAt(offset: Int, rows: SongRows, maxValue: Int = Int.MAX_VALUE): Int {
    if (rows.timingSections.isEmpty() || rows.stepOffsets.isEmpty() || rows.stepSections.size != rows.stepOffsets.size) return -1
    val reach = if (offset >= maxValue - POSITION_TOLERANCE) Float.MAX_VALUE else offset + POSITION_TOLERANCE
    val stop = rows.stepOffsets.indexOfLast { it <= reach }.coerceAtLeast(0)
    val section = rows.stepSections[stop]
    return rows.timingSections.indexOfLast { it <= section }
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
    // A stretch that starts at no row - the end of the song - is read as a whole.
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
 * Where a fling of the song that set off from [start] towards [target] may go: no further than a press of a step button
 * would take it from there ([nextStepTarget], [previousStepTarget]). A song not laid out yet has no steps, and one already
 * at the end it is flung towards has none left that way, so neither is held back.
 */
internal fun oneStepCappedTarget(start: Float, target: Float, rows: SongRows, viewportHeight: Int, window: ReadingWindow, maxValue: Int): Float {
    val from = start.toInt()
    return when {
        target > start -> nextStepTarget(from, rows, viewportHeight, window, maxValue)?.let { minOf(target, it.toFloat()) } ?: target
        target < start -> previousStepTarget(from, rows, viewportHeight, window, maxValue)?.let { maxOf(target, it.toFloat()) } ?: target
        else -> target
    }
}

/**
 * How much of the viewport a step reads the song through: all of it but what the top edge fades out ([top]) and what
 * the system bars cover at its bottom ([bottom]). A step that goes on within what did not fit on the screen moves the
 * song by that much less [overlap], the last line or two read, which the eye finds its place again by. [end] is the
 * empty space the scroll holds after the last line, above what the system bars cover, which is nothing to read.
 */
internal data class ReadingWindow(val top: Int = 0, val bottom: Int = 0, val end: Int = 0, val overlap: Int = 0) {

    /** How much of the song can be read at once. */
    fun visibleHeight(viewportHeight: Int) = viewportHeight - top - bottom

    /**
     * How far a step within a stop moves the song: never less than two thirds of what can be read at once, since at a
     * large text size on a small screen two lines of overlap would leave a page smaller than a line.
     */
    fun pageHeight(viewportHeight: Int): Int {
        val visibleHeight = visibleHeight(viewportHeight)
        return (visibleHeight - overlap.coerceAtMost(visibleHeight / 3)).coerceAtLeast(visibleHeight / 2).coerceAtLeast(MIN_PAGE_HEIGHT)
    }

    /** The height of a line of lyrics, which [overlap] is two of: the least a press should move the song by. */
    val lineHeight get() = overlap / 2
}

/**
 * The scroll position the next button and Page Down move the song at [scroll] to, or null only where the whole of it has
 * been on screen: a song may be read with nothing but a pedal pressing those keys, so no line of it may be out of their
 * reach, whatever the size of the screen and of the text.
 *
 * That is the next stop ([nextStepOffset]) wherever all of the stop the reader is in is on screen. A row or a section
 * that is taller than what can be read at once - a long verse on a phone held sideways, or any section at a large
 * enough text size - is paged through first, [ReadingWindow.pageHeight] at a time - or less, to bring the start of a line to
 * the top ([SongRows.lineTops]) - and the next stop is only stepped to
 * once its content's end is in view: from the top of a row taller than the screen the next row's divider is further
 * than the screen, and stepping to it would skip the part of this row in between. Only the content of a row counts,
 * never the empty space it is followed by (see [SongRows.bottoms]); a section, which is followed by nothing but the
 * gap before the next one, is all in view once the next one's top is. The last stop is paged through to the end of
 * the song, which is also why there is always a step left while any of the song is below the screen.
 *
 * A press that would move the song by less than a line - stops that the end of the song has clamped to within a few
 * pixels of each other, a fling that came to rest just above one - goes on from where it would have stopped, step by
 * step, for as long as that stays within what can be read at once past the reader, so that the walk passes over no
 * line the reader has not had on screen. A step that is small for a reason - the end of a row's content just below -
 * is taken as it is. Null is only returned once all of the song's content is on screen.
 */
internal fun nextStepTarget(scroll: Int, rows: SongRows, viewportHeight: Int, window: ReadingWindow, maxValue: Int): Int? {
    var target = nextStepTargetOnce(scroll, rows, viewportHeight, window, maxValue) ?: return null
    while (target - scroll < window.lineHeight) {
        val further = nextStepTargetOnce(target, rows, viewportHeight, window, maxValue)
            ?: return if (maxValue - window.end <= scroll + POSITION_TOLERANCE) null else target
        if (further > scroll + window.visibleHeight(viewportHeight)) break
        target = further
    }
    return target
}

/** One step of [nextStepTarget], however small. */
private fun nextStepTargetOnce(scroll: Int, rows: SongRows, viewportHeight: Int, window: ReadingWindow, maxValue: Int): Int? {
    if (rows.stepOffsets.isEmpty()) return null
    val next = nextStepOffset(scroll, rows.stepOffsets, maxValue)
    var limit = minOf(maxValue, next ?: Int.MAX_VALUE)
    // Where the last of the stop's content is at the bottom of what can be read, from which on there is nothing left to
    // page to. Past the last stop that is short of the end of the song by the space after it, so that a fling that
    // stopped a few pixels short has shown all there is and the next press goes on to the next song rather than
    // nudging this one; a page that reaches that space still goes on to the end, where the song always ends.
    var lastContent = if (next != null) next - window.visibleHeight(viewportHeight) else maxValue - window.end
    if (rows.isSteppedByRow) {
        val stop = stopAt(scroll, rows.stepOffsets)
        val bottom = if (stop >= 0) rows.bottoms.getOrNull(stop) else null
        if (bottom != null) {
            lastContent = bottom - (viewportHeight - window.bottom)
            limit = minOf(limit, lastContent)
        }
    }
    if (lastContent <= scroll + POSITION_TOLERANCE) return next
    // The furthest start of a line that a page reaches, so that the page begins with a whole line rather than with the
    // bottom half of one. None within the page - a staff of tablature taller than it - and the page is taken as it is.
    val pageHeight = window.pageHeight(viewportHeight)
    var page = scroll + pageHeight
    var lineStart: Int? = null
    for (top in rows.lineTops) {
        val candidate = top - window.top
        if (candidate > scroll + pageHeight / 2 && candidate <= page && (lineStart == null || candidate > lineStart)) lineStart = candidate
    }
    page = lineStart ?: page
    return minOf(page, limit)
}

/**
 * The scroll position the previous button and Page Up move the song at [scroll] to, or null only at the very top of it:
 * [nextStepTarget] the other way. The stop the reader is in, or the one before it, is paged back through where it does
 * not fit the screen, so that stepping back from a row lands on the end of a tall row before it rather than on its
 * top, and a short row before it is stepped to whole, never to the empty space that follows it. A press that would move
 * the song by less than a line goes on the same way as there.
 */
internal fun previousStepTarget(scroll: Int, rows: SongRows, viewportHeight: Int, window: ReadingWindow, maxValue: Int): Int? {
    var target = previousStepTargetOnce(scroll, rows, viewportHeight, window, maxValue) ?: return null
    while (scroll - target < window.lineHeight) {
        // Only the very top of the song hands off backwards, so a step that finds nothing further keeps the one it has.
        val further = previousStepTargetOnce(target, rows, viewportHeight, window, maxValue) ?: break
        if (further < scroll - window.visibleHeight(viewportHeight)) break
        target = further
    }
    return target
}

/** One step of [previousStepTarget], however small. */
private fun previousStepTargetOnce(scroll: Int, rows: SongRows, viewportHeight: Int, window: ReadingWindow, maxValue: Int): Int? {
    if (rows.stepOffsets.isEmpty()) return null
    val previous = previousStepOffset(scroll, rows.stepOffsets, maxValue) ?: return null
    // Everything between the two is in view from the stop before.
    if (scroll - previous <= window.visibleHeight(viewportHeight)) return previous
    val pageHeight = window.pageHeight(viewportHeight)
    // A page back begins with a whole line as well: the one nearest to a page above, but never further than that.
    var lineStart: Int? = null
    for (top in rows.lineTops) {
        val candidate = top - window.top
        if (candidate >= scroll - pageHeight && candidate < scroll - pageHeight / 2 && (lineStart == null || candidate < lineStart)) lineStart = candidate
    }
    var target = maxOf(lineStart ?: (scroll - pageHeight), previous)
    if (rows.isSteppedByRow) {
        val stop = stopAt(target, rows.stepOffsets)
        val bottom = if (stop >= 0) rows.bottoms.getOrNull(stop) else null
        if (bottom != null) target = minOf(target, maxOf(previous, bottom - (viewportHeight - window.bottom)))
    }
    return target.coerceIn(0, maxValue)
}

/** Whether [offset] is where a stop of [rows] is rested on, the top of the song counting as one. */
internal fun isStepStop(offset: Int, rows: SongRows, maxValue: Int) = offset == 0 || rows.stepOffsets.any { it.coerceIn(0, maxValue) == offset }

/**
 * The index of the stop the reader at [scroll] is past among [stepOffsets]: the lowest one above it, which in columns
 * read top to bottom need not be the last one listed. -1 above all of them.
 */
internal fun stopAt(scroll: Int, stepOffsets: List<Int>): Int {
    var stop = -1
    stepOffsets.forEachIndexed { index, offset ->
        if (offset <= scroll + POSITION_TOLERANCE && (stop < 0 || offset > stepOffsets[stop])) stop = index
    }
    return stop
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
 * [scroll] itself, where the reader is past its top - and null only at the very top of the song, which is always one.
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
 * The stops of [rows] a song that scrolls no further than [maxValue] can be brought to, ascending and each once: a stop
 * too close to the end of the song to reach the top of the screen is rested on where the song ends, together with any
 * other that is, since that is where the steps take the reader to both. Of a run of stops each closer than [minGap] to
 * the one before, only the last is kept, since a press walks through the others (see [nextStepTarget]). What the
 * progress indicator counts, so a song that does not scroll at all has one.
 */
internal fun reachableStops(rows: SongRows, maxValue: Int, minGap: Int = 0): List<Int> {
    val stops = rows.stepOffsets.map { it.coerceIn(0, maxValue) }.distinct().sorted()
    if (minGap <= 0) return stops
    val kept = mutableListOf<Int>()
    stops.forEachIndexed { index, stop ->
        if (index > 0 && stop - stops[index - 1] < minGap) kept[kept.lastIndex] = stop else kept += stop
    }
    return kept
}

/**
 * Where the reader at [scroll] is among [stops] (see [reachableStops]), in stops: the index of one where the song rests on
 * it, and in between two by how far it has scrolled from the one to the other. The first stop is at the top of the song,
 * so nothing is above it.
 */
internal fun stopProgress(scroll: Int, stops: List<Int>): Float {
    if (stops.isEmpty() || scroll <= stops.first()) return 0f
    for (i in 1 until stops.size) {
        val start = stops[i - 1]
        val end = stops[i]
        if (scroll < end) return i - 1 + (scroll - start).toFloat() / (end - start)
    }
    return stops.lastIndex.toFloat()
}

/** How far apart two scroll positions may be and still count as one, since an animated scroll ends on a rounded pixel. */
internal const val POSITION_TOLERANCE = 1f

/** The least a page step moves the song by, so that a viewport with no room left to read in still gets somewhere. */
private const val MIN_PAGE_HEIGHT = 8
