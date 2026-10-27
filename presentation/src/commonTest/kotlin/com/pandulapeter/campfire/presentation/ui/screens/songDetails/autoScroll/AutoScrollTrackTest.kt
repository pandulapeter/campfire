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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes

internal class AutoScrollTrackTest {

    @Test
    fun `a line moves at its own height per line time, however tall it is drawn`() {
        val track = trackOf(tops = listOf(0, 100, 300), bottoms = listOf(100, 300, 400), playedLines = listOf(1, 1, 1))!!
        assertEquals(25f, track.speedAt(position = 50f, secondsPerLine = 4.0))
        assertEquals(50f, track.speedAt(position = 150f, secondsPerLine = 4.0))
        assertEquals(3, track.lineCount)
    }

    @Test
    fun `what plays no line is passed at the speed the song averages over its lines`() {
        val track = trackOf(tops = listOf(0, 200, 300), bottoms = listOf(200, 300, 500), playedLines = listOf(0, 1, 1))!!
        // The lines span 100 and 200 pixels: 150 a line, over 3 seconds.
        assertEquals(50f, track.speedAt(position = 10f, secondsPerLine = 3.0))
        assertEquals(2, track.lineCount)
    }

    @Test
    fun `a position above the first line is played at the first one's speed`() {
        val track = trackOf(tops = listOf(100, 200), bottoms = listOf(200, 300), playedLines = listOf(2, 1))!!
        assertEquals(25f, track.speedAt(position = 0f, secondsPerLine = 2.0))
    }

    @Test
    fun `a chunk of no height plays none of its lines`() {
        val track = trackOf(tops = listOf(0, 100, 100), bottoms = listOf(100, 100, 200), playedLines = listOf(1, 3, 1))!!
        assertEquals(2, track.lineCount)
        assertEquals(25f, track.speedAt(position = 150f, secondsPerLine = 4.0))
    }

    @Test
    fun `a stretch is timed by the speed of every chunk it crosses, past either end at the speed of the end's chunk`() {
        val track = trackOf(tops = listOf(0, 100, 300), bottoms = listOf(100, 300, 400), playedLines = listOf(1, 1, 1))!!
        // 50 pixels at 25 a second, then 100 at 50 a second.
        assertEquals(4.0, track.secondsBetween(from = 50f, to = 200f, secondsPerLine = 4.0))
        // 100 pixels before the first chunk at its 25 a second, and 100 past the last at its 25 a second.
        assertEquals(4.0, track.secondsBetween(from = -100f, to = 0f, secondsPerLine = 4.0))
        assertEquals(4.0, track.secondsBetween(from = 400f, to = 500f, secondsPerLine = 4.0))
        assertEquals(0.0, track.secondsBetween(from = 200f, to = 100f, secondsPerLine = 4.0))
    }

    @Test
    fun `a song with no line to play has no track`() {
        assertNull(trackOf(tops = listOf(0), bottoms = listOf(100), playedLines = listOf(0)))
        assertNull(AutoScrollTrack.of(SongRows()))
    }

    @Test
    fun `a song's own duration is shared out over its lines, and without one two bars a line are played at its tempo`() {
        assertEquals(5.0, ownSecondsPerLine(duration = 3.minutes, lineCount = 36, bpm = 120, beatsPerBar = 4))
        assertEquals(4.0, ownSecondsPerLine(duration = null, lineCount = 36, bpm = 120, beatsPerBar = 4))
        assertEquals(6.0, ownSecondsPerLine(duration = null, lineCount = 36, bpm = 60, beatsPerBar = 3))
    }

    @Test
    fun `a faster speed shortens every line by as much`() {
        assertEquals(2.0, secondsPerLineAt(ownSecondsPerLine = 4.0, speedPercent = 200))
        assertEquals(4.0, secondsPerLineAt(ownSecondsPerLine = 4.0, speedPercent = SongLayout.DEFAULT_AUTO_SCROLL_SPEED))
    }

    @Test
    fun `the slider's ends are the range's, its middle the song's own pace, and a speed near that snaps to it`() {
        assertEquals(SongLayout.AUTO_SCROLL_SPEED_RANGE.first, autoScrollSpeedAt(0f))
        assertEquals(SongLayout.AUTO_SCROLL_SPEED_RANGE.last, autoScrollSpeedAt(1f))
        assertEquals(SongLayout.DEFAULT_AUTO_SCROLL_SPEED, autoScrollSpeedAt(0.5f))
        assertEquals(SongLayout.DEFAULT_AUTO_SCROLL_SPEED, autoScrollSpeedAt(autoScrollSpeedFraction(102)))
        assertEquals(150, autoScrollSpeedAt(autoScrollSpeedFraction(150)))
    }

    private fun trackOf(tops: List<Int>, bottoms: List<Int>, playedLines: List<Int>) = AutoScrollTrack.of(
        SongRows(lineTops = tops, lineBottoms = bottoms, lineSections = tops.indices.toList(), playedLines = playedLines),
    )
}
