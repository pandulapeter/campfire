/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails.autoScroll

import com.pandulapeter.campfire.data.model.preferences.SongLayout
import com.pandulapeter.campfire.presentation.ui.songView.SongRows
import kotlin.time.Duration

/**
 * The line chunks of a song laid out in one column, as the scroll that plays it by itself reads them: where each starts,
 * how far it runs until the next one does ([spans]), and how many lines of it take a moment to play ([playedLines]). The
 * song's time is spent on those lines, a line each, so a line that wraps, a larger text size, or a narrower window move
 * the song at another speed on screen but at the same pace through it. What plays no line - a heading on its own, a
 * section folded away, the Chords and the Scales sections, the gap between two sections - is passed at the speed the song
 * averages over its lines, so that it neither stops the scroll nor jumps it. A chunk with no height (the hidden lines of a
 * folded run) plays none of its lines, since nothing of it passes the reading line.
 *
 * A position above the first chunk is played at the first one's speed and one past the last at the last one's, as the
 * scroll reads them, so that [secondsBetween] times exactly what [speedAt] plays.
 */
internal class AutoScrollTrack private constructor(
    private val tops: List<Int>,
    private val spans: IntArray,
    private val playedLines: IntArray,
    /** How many lines of the song take a moment to play. */
    val lineCount: Int,
    private val averageLineSpan: Double,
) {

    /**
     * How fast the song moves on screen, in pixels a second, where [position] (in the scroll's coordinates) is the line
     * being played, each line taking [secondsPerLine].
     */
    fun speedAt(position: Float, secondsPerLine: Double) = speedOf(tops.indexOfLast { it <= position }.coerceAtLeast(0), secondsPerLine).toFloat()

    /** How long playing takes the line being played from [from] to [to], each line taking [secondsPerLine]. */
    fun secondsBetween(from: Float, to: Float, secondsPerLine: Double): Double {
        if (to <= from) return 0.0
        var seconds = 0.0
        tops.indices.forEach { index ->
            val start = if (index == 0) Float.NEGATIVE_INFINITY else tops[index].toFloat()
            val end = tops.getOrNull(index + 1)?.toFloat() ?: Float.POSITIVE_INFINITY
            val overlap = minOf(to, end) - maxOf(from, start)
            if (overlap > 0f) seconds += overlap / speedOf(index, secondsPerLine)
        }
        return seconds
    }

    private fun speedOf(index: Int, secondsPerLine: Double): Double {
        val lines = playedLines[index]
        val lineSpan = if (lines > 0) spans[index].toDouble() / lines else averageLineSpan
        return lineSpan / secondsPerLine
    }

    companion object {

        /** The track of [rows], null where they describe no line of a single column that takes a moment to play. */
        fun of(rows: SongRows): AutoScrollTrack? {
            if (!rows.hasLines || rows.playedLines.size != rows.lineTops.size) return null
            val tops = rows.lineTops
            val spans = IntArray(tops.size) { index -> ((tops.getOrNull(index + 1) ?: rows.lineBottoms[index]) - tops[index]).coerceAtLeast(0) }
            val playedLines = IntArray(tops.size) { index -> if (spans[index] > 0) rows.playedLines[index] else 0 }
            val lineCount = playedLines.sum()
            if (lineCount == 0) return null
            val playedSpan = tops.indices.filter { playedLines[it] > 0 }.sumOf { spans[it].toLong() }
            return AutoScrollTrack(
                tops = tops,
                spans = spans,
                playedLines = playedLines,
                lineCount = lineCount,
                averageLineSpan = playedSpan.toDouble() / lineCount,
            )
        }
    }
}

/**
 * How long a line of a song takes at its own pace: its [duration] shared out over its [lineCount] lines where the file
 * gives one, and otherwise [BARS_PER_LINE] bars of [beatsPerBar] beats at [bpm], the tempo counting the bar's beats as the
 * click does. Two bars of 4/4 at 120 is four seconds, which is also about what a line of a song with no tempo takes.
 */
internal fun ownSecondsPerLine(duration: Duration?, lineCount: Int, bpm: Int, beatsPerBar: Int): Double =
    if (duration != null && duration.isPositive() && lineCount > 0) {
        duration.inWholeMilliseconds / MILLIS_PER_SECOND / lineCount
    } else {
        BARS_PER_LINE * beatsPerBar.coerceAtLeast(1) * SECONDS_PER_MINUTE / bpm.coerceAtLeast(1)
    }

/** [ownSecondsPerLine] played at [speedPercent] of the song's own pace. */
internal fun secondsPerLineAt(ownSecondsPerLine: Double, speedPercent: Int) = ownSecondsPerLine * SongLayout.DEFAULT_AUTO_SCROLL_SPEED / speedPercent.coerceAtLeast(1)

private const val BARS_PER_LINE = 2
private const val SECONDS_PER_MINUTE = 60.0
private const val MILLIS_PER_SECOND = 1000.0
