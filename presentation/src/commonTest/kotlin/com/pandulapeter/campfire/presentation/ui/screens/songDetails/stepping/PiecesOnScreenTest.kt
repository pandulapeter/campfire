/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails.stepping

import com.pandulapeter.campfire.presentation.ui.songView.SongPiece
import com.pandulapeter.campfire.presentation.ui.songView.SongRows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

internal class PiecesOnScreenTest {

    // Ten lines 40 px tall, the first five a section that a 200 px screen cannot hold whole.
    private val lines = List(10) { SongPiece(start = it * 40, end = it * 40 + 40, section = if (it < 5) 0 else 1, items = (it % 5)..(it % 5)) }
    // A line of lyrics is half the overlap: 40 px, as tall as the lines.
    private val window = ReadingWindow(overlap = 80)

    @Test
    fun `a song not laid out yet shows nothing`() = assertNull(piecesOnScreen(0, 200, SongRows(), window))

    @Test
    fun `only the lines of a long section on the screen are shown without the rest of the section`() {
        val rows = SongRows(pieces = lines)
        assertEquals(lines.subList(0, 5), piecesOnScreen(0, 200, rows, window))
        assertEquals(lines.subList(3, 8), piecesOnScreen(120, 200, rows, window))
    }

    @Test
    fun `a line the screen's end cuts by less than half of it is not shown while one cut by half is`() {
        val rows = SongRows(pieces = lines)
        assertEquals(lines.subList(0, 5), piecesOnScreen(0, 210, rows, window))
        assertEquals(lines.subList(0, 6), piecesOnScreen(0, 220, rows, window))
    }

    @Test
    fun `what the bars at the screen's end cover is off the screen`() =
        assertEquals(lines.subList(0, 4), piecesOnScreen(0, 200, SongRows(pieces = lines), window.copy(bottom = 40)))

    @Test
    fun `the top fade hides the lines under it once the song is scrolled into a row while a rested row starts unfaded`() {
        val faded = window.copy(top = 80)
        val rows = SongRows(restingOffsets = listOf(0, 200), pieces = lines)
        assertEquals(lines.subList(5, 10), piecesOnScreen(200, 200, rows, faded))
        assertEquals(lines.subList(7, 10), piecesOnScreen(200, 200, rows.copy(restingOffsets = listOf(0)), faded))
    }

    @Test
    fun `the columns of a row side by side are all shown`() {
        val columns = listOf(
            SongPiece(start = 0, end = 160, section = 0, items = 0..3),
            SongPiece(start = 0, end = 120, section = 1, items = 0..2),
            SongPiece(start = 300, end = 400, section = 2, items = 0..1),
        )
        assertEquals(columns.subList(0, 2), piecesOnScreen(0, 300, SongRows(pieces = columns), window))
    }

    @Test
    fun `a strip shows the columns across the screen without the next one past its edge`() {
        val strip = listOf(
            SongPiece(start = 0, end = 300, section = 0, items = 0..0),
            SongPiece(start = 340, end = 640, section = 1, items = 0..0),
            SongPiece(start = 680, end = 980, section = 2, items = 0..0),
        )
        val rows = SongRows(restingOffsets = listOf(0, 340, 680), isAcross = true, pieces = strip)
        assertEquals(strip.subList(0, 2), piecesOnScreen(0, 680, rows, window.across()))
        assertEquals(strip.subList(1, 3), piecesOnScreen(340, 680, rows, window.across()))
    }
}
