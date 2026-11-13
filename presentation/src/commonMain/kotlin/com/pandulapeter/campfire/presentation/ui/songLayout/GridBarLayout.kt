/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songLayout

/**
 * The sizes a drawn grid is laid out with, in whatever unit the caller draws in (pixels on screen, points in the PDF),
 * all of them in ems of the chord text ([em], its size) apart from [textHeight], the height of a line of it.
 */
internal data class GridMetrics(
    val em: Float,
    val textHeight: Float,
) {
    val beatWidth get() = em * 1.3f
    val segmentPadding get() = em * 0.4f
    val barGap get() = em * 0.35f
    val marginGap get() = em * 0.6f
    val trailingGap get() = em * 0.6f
    val heavyInset get() = em * 0.3f
    val repeatInset get() = em * 0.65f
    val boxHeight get() = textHeight + em * 0.7f
    val voltaHeight get() = em * 0.95f
    val rowGap get() = em * 0.35f
    val cornerRadius get() = em * 0.3f
    val strokeWidth get() = em * 0.06f
    val heavyWidth get() = em * 0.16f
    val dotRadius get() = em * 0.11f

    /** How far inside the box its segments start or end next to an [edge], which leaves room for its stroke and dots. */
    fun insetOf(edge: GridEdge) = when (edge) {
        GridEdge.PLAIN -> 0f
        GridEdge.HEAVY -> heavyInset
        GridEdge.REPEAT -> repeatInset
    }
}

/**
 * The measured columns of one run of grid lines, which every line of it is placed by ([placeLine]) at whatever width it
 * is given: a column is as wide as the widest bar standing in it on any line, so that bars line up across the lines,
 * and the run's margin labels stand in a column of their own before them.
 *
 * A bar is as wide as its segments, each as wide as its beats or, where its name is wider, the name and its padding; a bar
 * narrower than its column gives the extra to its segments by their beats, so that a beat keeps one width along a bar.
 * [labelWidth] measures what a segment's name or sign is drawn as ([gridSegmentLabel]), [textWidth] a margin label or a
 * trailing text.
 */
internal class GridBarLayout(
    val lines: List<GridBarLine>,
    val metrics: GridMetrics,
    private val labelWidth: (String) -> Float,
    private val textWidth: (String) -> Float,
) {
    private val naturalWidths = lines.map { line -> line.bars.map { bar -> bar.naturalWidth() } }

    val columnWidths: List<Float> = (0 until (lines.maxOfOrNull { it.bars.size } ?: 0)).map { index ->
        naturalWidths.maxOf { it.getOrNull(index) ?: 0f }
    }

    /** Where the bars start: after the widest margin label of the run, or at the start where no line has one. */
    val barsStart = lines.mapNotNull { it.margin }.maxOfOrNull { textWidth(it) + metrics.marginGap } ?: 0f

    /**
     * How many bars every row of the run holds at [maxWidth]: the most that fit on every row of every line, so that a
     * wrapped run keeps its columns; at least one, which a bar wider than the whole width overflows.
     */
    fun barsPerRow(maxWidth: Float): Int {
        val count = columnWidths.size
        if (count == 0) return 1
        return (count downTo 1).firstOrNull { perRow ->
            (0 until count step perRow).all { start -> barsStart + rowWidth(start, minOf(start + perRow, count)) <= maxWidth }
        } ?: 1
    }

    /** The widest any line of the run is unbroken, which a right-to-left song moves the run to its line end by. */
    val width: Float by lazy(LazyThreadSafetyMode.NONE) { lines.indices.maxOfOrNull { placeLine(it, Float.POSITIVE_INFINITY).width } ?: 0f }

    /** The line at [index] broken into rows of [barsPerRow] bars at [maxWidth]. */
    fun placeLine(index: Int, maxWidth: Float): GridLinePlacement {
        val line = lines[index]
        val perRow = barsPerRow(maxWidth)
        val bars = line.bars.mapIndexed { barIndex, bar ->
            val rowStart = barIndex - barIndex % perRow
            val x = barsStart + rowWidth(rowStart, barIndex) + if (barIndex > rowStart) metrics.barGap else 0f
            PlacedGridBar(
                bar = bar,
                row = barIndex / perRow,
                x = x,
                width = columnWidths[barIndex],
                segments = bar.placeSegments(x, columnWidths[barIndex], naturalWidths[index][barIndex]),
            )
        }
        val trailing = line.trailing?.let { text ->
            val last = bars.lastOrNull()
            val x = last?.let { it.x + it.width + metrics.trailingGap } ?: barsStart
            if (last != null && x + textWidth(text) > maxWidth) PlacedGridText(text, row = last.row + 1, x = barsStart) else PlacedGridText(text, row = last?.row ?: 0, x = x)
        }
        val rowCount = maxOf(bars.lastOrNull()?.row ?: 0, trailing?.row ?: 0) + 1
        val hasVolta = BooleanArray(rowCount).also { rows -> bars.forEach { if (it.bar.volta != null) rows[it.row] = true } }
        val rowTops = FloatArray(rowCount)
        var y = 0f
        repeat(rowCount) { row ->
            if (row > 0) y += metrics.rowGap
            if (hasVolta[row]) y += metrics.voltaHeight
            rowTops[row] = y
            y += metrics.boxHeight
        }
        val width = maxOf(
            bars.maxOfOrNull { it.x + it.width } ?: 0f,
            trailing?.let { it.x + textWidth(it.text) } ?: 0f,
            line.margin?.let { textWidth(it) } ?: 0f,
        )
        return GridLinePlacement(margin = line.margin, bars = bars, trailing = trailing, rowTops = rowTops.toList(), height = y, width = width)
    }

    /** The bars from [from] until [until] side by side, the gaps between them included. */
    private fun rowWidth(from: Int, until: Int): Float {
        if (until <= from) return 0f
        return (from until until).sumOf { columnWidths[it].toDouble() }.toFloat() + metrics.barGap * (until - from - 1)
    }

    private fun GridBar.naturalWidth() = metrics.insetOf(opening) + metrics.insetOf(closing) + segments.sumOf { it.ownWidth().toDouble() }.toFloat()

    private fun GridSegment.ownWidth(): Float {
        val label = gridSegmentLabel(this)
        return maxOf(beats * metrics.beatWidth, if (label == null) 0f else labelWidth(label) + metrics.segmentPadding * 2)
    }

    private fun GridBar.placeSegments(x: Float, width: Float, naturalWidth: Float): List<PlacedGridSegment> {
        val extra = width - naturalWidth
        val totalBeats = segments.sumOf { it.beats }.coerceAtLeast(1)
        var segmentX = x + metrics.insetOf(opening)
        return segments.map { segment ->
            val segmentWidth = segment.ownWidth() + extra * segment.beats / totalBeats
            PlacedGridSegment(segment, x = segmentX, width = segmentWidth).also { segmentX += segmentWidth }
        }
    }
}

/** One line of a [GridBarLayout] placed at a width: its bars by row, each row's top ([rowTops]), and its whole size. */
internal data class GridLinePlacement(
    val margin: String?,
    val bars: List<PlacedGridBar>,
    val trailing: PlacedGridText?,
    val rowTops: List<Float>,
    val height: Float,
    val width: Float,
)

/** A bar's box, [x] from the line's start on its [row], with its segments at their own [PlacedGridSegment.x]. */
internal data class PlacedGridBar(
    val bar: GridBar,
    val row: Int,
    val x: Float,
    val width: Float,
    val segments: List<PlacedGridSegment>,
)

internal data class PlacedGridSegment(
    val segment: GridSegment,
    val x: Float,
    val width: Float,
)

internal data class PlacedGridText(
    val text: String,
    val row: Int,
    val x: Float,
)

/** What a segment is drawn as: its chords' names side by side, or the repeat sign; a held beat is a line, not a text. */
internal fun gridSegmentLabel(segment: GridSegment) = when (segment) {
    is GridSegment.Chord -> segment.names.joinToString(" ")
    is GridSegment.Hold -> null
    is GridSegment.Repeat -> "%".repeat(segment.bars)
}

/**
 * The span a repeat sign is centred over, from the start of [bar]'s segment holding it: its own segment for `%`, and to
 * the end of the next bar for `%%` where that bar stands on the same row.
 */
internal fun GridLinePlacement.repeatSpan(barIndex: Int, segment: PlacedGridSegment): ClosedFloatingPointRange<Float> {
    val repeat = segment.segment as GridSegment.Repeat
    val next = bars.getOrNull(barIndex + 1)
    return if (repeat.bars > 1 && next != null && next.row == bars[barIndex].row && next.segments.isEmpty()) {
        segment.x..(next.x + next.width)
    } else {
        segment.x..(segment.x + segment.width)
    }
}
