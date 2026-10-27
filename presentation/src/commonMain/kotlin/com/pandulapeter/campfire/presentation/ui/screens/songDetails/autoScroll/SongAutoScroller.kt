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

import com.pandulapeter.campfire.presentation.ui.screens.songDetails.stepping.RowSnapFlingBehavior
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlin.time.Duration

/**
 * Plays one page's song by scrolling it, laid out in one column (`SongLayout.CONTINUOUS`): the line at the reading line,
 * a third of the way down what can be read at once, moves on at the pace [AutoScrollTrack] times it to. The pace is
 * read from where the song is rather than from a clock, so a drag, a fling, or a nudge only moves the reader, and the
 * scroll goes on from wherever they let go.
 */
internal class SongAutoScroller(
    private val scrollState: ScrollState,
    private val flingBehavior: RowSnapFlingBehavior,
) {
    private val track by derivedStateOf { AutoScrollTrack.of(flingBehavior.rows) }

    /** What the song's own pace is worked out from, which its page writes whenever any of it changes. */
    var pace by mutableStateOf(OwnPace())

    /**
     * How long playing at [speedPercent] of the song's own pace takes from where the song is to where it can scroll no
     * further, which is where playing stops: null before it has been laid out. It reads the scroll, so it is to be read
     * through a derived state that rounds it.
     */
    fun remainingSeconds(speedPercent: Int): Double? {
        val currentTrack = track ?: return null
        return currentTrack.secondsBetween(
            from = readingLineAt(scrollState.value).toFloat(),
            to = readingLineAt(scrollState.maxValue).toFloat(),
            secondsPerLine = secondsPerLineAt(pace.secondsPerLine(currentTrack.lineCount), speedPercent),
        )
    }

    /**
     * Where the nudge being animated is headed, which the next one is counted from: two quick presses of a pedal are two
     * nudges from where the song was, not one and a bit from wherever the first had got to. State, since whether there
     * is anything left to nudge to is read from it.
     */
    private var nudgeTarget by mutableStateOf<Int?>(null)

    private val nudgeOrigin get() = nudgeTarget ?: scrollState.value

    /** Whether there is anything left to scroll to in [direction] (-1 up, 1 down), counted from where a nudge still moving is headed. */
    fun canNudge(direction: Int) = if (direction < 0) nudgeOrigin > 0 else nudgeOrigin < scrollState.maxValue

    /**
     * Moves the song [NUDGE_LINES] lines in [direction], the way a pedal does: whatever is playing goes on from there. A
     * line is a chorded one, a line of chords over a line of lyrics, which is what the reading window's overlap is. A
     * press while a nudge moves counts from where it is headed.
     */
    suspend fun nudge(direction: Int) {
        val distance = flingBehavior.readingWindow.overlap.coerceAtLeast(MIN_NUDGE_PX) * NUDGE_LINES
        val target = (nudgeOrigin + distance * direction).coerceIn(0, scrollState.maxValue)
        // Set before the animation below cancels the one still moving, so that its finally leaves this target alone.
        nudgeTarget = target
        try {
            scrollState.animateScrollTo(target, tween(durationMillis = NUDGE_DURATION, easing = FastOutSlowInEasing))
        } finally {
            if (nudgeTarget == target) nudgeTarget = null
        }
    }

    /** Whether there is anything left to play: the song laid out, with more of it to scroll to. */
    val canPlay get() = track != null && scrollState.canScrollForward

    /**
     * Scrolls the song at [speedPercent] of its own pace, frame by frame, for as long as it is not cancelled, returning
     * once it has reached the end. A frame in which anything else moves the song - a finger, a fling, a nudge - is left
     * to that, and so is one longer than [MAX_FRAME_SECONDS], so that a stall is never made up for with a jump. A frame
     * in which a new layout is still to be followed is skipped too, since the reader is put back on their place in it
     * first ([RowSnapFlingBehavior.isFollowingLayout]).
     */
    suspend fun play(speedPercent: () -> Int) {
        var lastFrame = withFrameNanos { it }
        while (true) {
            val frame = withFrameNanos { it }
            val seconds = ((frame - lastFrame) / NANOS_PER_SECOND).coerceAtMost(MAX_FRAME_SECONDS)
            lastFrame = frame
            val currentTrack = track ?: continue
            if (!scrollState.canScrollForward) return
            if (scrollState.isScrollInProgress || flingBehavior.isFollowingLayout) continue
            val speed = currentTrack.speedAt(readingLineAt(scrollState.value).toFloat(), secondsPerLineAt(pace.secondsPerLine(currentTrack.lineCount), speedPercent()))
            scrollState.dispatchRawDelta(speed * seconds)
            flingBehavior.noteReaderMoved()
        }
    }

    /** Where the line being played is with the song scrolled to [scroll]. */
    private fun readingLineAt(scroll: Int): Int {
        val window = flingBehavior.readingWindow
        return scroll + window.top + window.visibleHeight(scrollState.viewportSize) / READING_LINE_FRACTION
    }

    /** The song's own pace, see [ownSecondsPerLine]. */
    data class OwnPace(
        val duration: Duration? = null,
        val bpm: Int = DEFAULT_BPM,
        val beatsPerBar: Int = DEFAULT_BEATS_PER_BAR,
    ) {
        fun secondsPerLine(lineCount: Int) = ownSecondsPerLine(duration = duration, lineCount = lineCount, bpm = bpm, beatsPerBar = beatsPerBar)
    }
}

/** The reading line is this fraction of what can be read at once down from its top: the line being played, with what comes next under it. */
private const val READING_LINE_FRACTION = 3
private const val NUDGE_LINES = 2
private const val MIN_NUDGE_PX = 32
private const val NUDGE_DURATION = 250
private const val NANOS_PER_SECOND = 1_000_000_000f
private const val MAX_FRAME_SECONDS = 0.1f
private const val DEFAULT_BPM = 120
private const val DEFAULT_BEATS_PER_BAR = 4
