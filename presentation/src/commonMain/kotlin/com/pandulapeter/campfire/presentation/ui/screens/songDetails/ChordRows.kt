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

import com.pandulapeter.campfire.presentation.ui.chords.MAX_SONG_CHORDS

/**
 * Where each row of a Chords section's diagrams starts when the section is [width] wide, as the index of its first
 * cell: the cells in their order, [gap] apart, as few rows as the width allows and a cell wider than [width] a row of
 * its own, the cells shared out between those rows as evenly as their order lets them (see [balancedRowStarts]).
 * Worked out without composing anything, so that the section can be cut between its rows (see [chordSlotCount]).
 */
internal fun chordRowStarts(cellWidths: IntArray, gap: Int, width: Int): IntArray =
    balancedRowStarts(FloatArray(cellWidths.size) { cellWidths[it].toFloat() }, gap.toFloat(), width.toFloat())

/**
 * Where each row of [cellWidths] starts, [gap] apart within [width]: as many rows as filling each one before starting
 * the next would take, but with the cells shared out between them so that the rows are as close to one width as their
 * order lets them be — nine diagrams six of which fit on a line are five over four rather than six over three, and ten
 * of which four fit are four, three and three rather than four, four and two — since a last row left short makes the
 * section look as if it had run out of room rather than chords. Of the ways to cut the cells into that many rows none
 * wider than [width] (a cell wider than it still a row of its own), the one whose row widths have the smallest sum of
 * squares wins, which is the most even one, the rows always holding the same cells and gaps between them; a tie goes to
 * the longer rows first. Shared by the song details screen and the PDF, which lay the diagrams out alike.
 */
internal fun balancedRowStarts(cellWidths: FloatArray, gap: Float, width: Float): IntArray {
    val greedy = greedyRowStarts(cellWidths, gap, width)
    val rowCount = greedy.size
    if (rowCount <= 1) return greedy
    val count = cellWidths.size
    // The cheapest way to lay the cells from each one to the end out in each number of rows, and where its first row
    // ends, worked out from the last cell backwards.
    val cost = Array(rowCount + 1) { DoubleArray(count + 1) { Double.POSITIVE_INFINITY } }
    val rowEnd = Array(rowCount + 1) { IntArray(count + 1) }
    cost[0][count] = 0.0
    for (rows in 1..rowCount) {
        for (first in count - 1 downTo 0) {
            var run = 0f
            for (end in first + 1..count) {
                run += cellWidths[end - 1] + if (end - 1 > first) gap else 0f
                if (run > width && end - 1 > first) break
                val total = run.toDouble() * run + cost[rows - 1][end]
                // Not strictly cheaper only on a tie, which then keeps the longer first row.
                if (total <= cost[rows][first]) {
                    cost[rows][first] = total
                    rowEnd[rows][first] = end
                }
            }
        }
    }
    if (cost[rowCount][0].isInfinite()) return greedy
    val starts = IntArray(rowCount)
    var first = 0
    for (row in 0 until rowCount) {
        starts[row] = first
        first = rowEnd[rowCount - row][first]
    }
    return starts
}

/** Where each row of [cellWidths] starts when every row takes as many cells as fit in [width] before the next begins. */
private fun greedyRowStarts(cellWidths: FloatArray, gap: Float, width: Float): IntArray {
    if (cellWidths.isEmpty()) return IntArray(0)
    val starts = mutableListOf(0)
    var used = cellWidths[0]
    for (cell in 1 until cellWidths.size) {
        val next = used + gap + cellWidths[cell]
        if (next > width) {
            starts += cell
            used = cellWidths[cell]
        } else {
            used = next
        }
    }
    return starts.toIntArray()
}

/**
 * How many slots a Chords section of [cellCount] cells is drawn as, the most rows it can have: one per cell, up to
 * [MAX_CHORD_SLOTS], the last one holding whatever rows are left. Never fewer than one, which holds the header.
 */
internal fun chordSlotCount(cellCount: Int) = cellCount.coerceIn(1, MAX_CHORD_SLOTS)

/**
 * The rows a chunk holding the slots from [firstSlot] to [lastSlot] draws when the section has [rowCount] rows at its
 * width: the rows those slots name, and every row after them where the chunk ends the section ([isLastSlot]). Empty
 * where the width has no row for its first slot, which then has no height, and nothing is cut in front of it.
 */
internal fun chordSlotRows(rowCount: Int, firstSlot: Int, lastSlot: Int, isLastSlot: Boolean): IntRange =
    if (firstSlot >= rowCount) IntRange.EMPTY else firstSlot..(if (isLastSlot) rowCount - 1 else minOf(lastSlot, rowCount - 1))

/**
 * The most slots a Chords section is cut into: one per chord the song details screen ever lists ([MAX_SONG_CHORDS]),
 * so that every row it can wrap into is a slot of its own and the last one never holds more than one row there. Only
 * the editor's preview, whose definitions are not bounded, has more cells, and it is never cut.
 */
internal const val MAX_CHORD_SLOTS = MAX_SONG_CHORDS
