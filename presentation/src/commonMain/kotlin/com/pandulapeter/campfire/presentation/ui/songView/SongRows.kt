/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songView

import com.pandulapeter.campfire.presentation.ui.songView.model.sectionChunkStarts
import kotlin.math.roundToInt
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
 * what it is still called once a new layout has flowed the sections into different rows or columns (see `stepping.ReadingAnchor`).
 *
 * [lineTops] are where the pieces a single column is made of start - every section, and within one the chunks it may be
 * cut into, which start between two of its lines (see [sectionChunkStarts]) - so that a step that pages through what
 * does not fit the screen can bring a line to the top of it rather than half of one. [lineBottoms] are where each of
 * those pieces ends and [lineSections] the section each belongs to, which together name a line in terms another layout
 * of the song still has (see `stepping.LineAnchor`). [playedLines] are how many lines each of them takes a moment to play
 * (see `SongUnits.playedLineCount`), which is what a song that scrolls by itself is timed by (see `autoScroll.AutoScrollTrack`).
 *
 * [timingSections] are the sections that change the tempo or the time signature the song is played in from there on,
 * ascending, each of which starts a stop of its own wherever the song is stepped by rows (see `stepping.timingIndexAt`). A change
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
    val playedLines: List<Int> = emptyList(),
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
