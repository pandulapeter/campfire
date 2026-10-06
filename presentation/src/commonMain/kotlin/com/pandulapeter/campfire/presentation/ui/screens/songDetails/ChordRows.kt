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
 * cell: the cells in their order, [gap] apart, a row ending before the cell that would take it past [width], and a
 * cell wider than [width] a row of its own. What the `FlowRow` the section used to be did, worked out without
 * composing anything, so that the section can be cut between its rows (see [chordSlotCount]).
 */
internal fun chordRowStarts(cellWidths: IntArray, gap: Int, width: Int): IntArray {
    if (cellWidths.isEmpty()) return IntArray(0)
    val starts = mutableListOf(0)
    var used = cellWidths[0].toLong()
    for (cell in 1 until cellWidths.size) {
        val next = used + gap + cellWidths[cell]
        if (next > width) {
            starts += cell
            used = cellWidths[cell].toLong()
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
