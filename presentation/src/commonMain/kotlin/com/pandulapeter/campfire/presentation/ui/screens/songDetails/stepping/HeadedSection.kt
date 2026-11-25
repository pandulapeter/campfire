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

import com.pandulapeter.campfire.presentation.ui.songView.SongRows

/**
 * The section a scroll at [offset] shows first at the top of the screen, by its index among the song's sections: the
 * section of the first line starting at or below it where the song is a column of lines ([SongRows.hasLines]), else the
 * section the stop at or above it starts with (as [timingIndexAt] reads it), the last at the end of the song,
 * [maxValue]. Null before the song has been laid out.
 */
internal fun sectionAt(offset: Int, rows: SongRows, maxValue: Int = Int.MAX_VALUE): Int? {
    if (rows.hasLines) {
        val line = rows.lineTops.indexOfFirst { it >= offset - POSITION_TOLERANCE }
        return rows.lineSections.getOrNull(if (line < 0) rows.lineTops.lastIndex else line)
    }
    if (rows.stepOffsets.isEmpty() || rows.stepSections.size != rows.stepOffsets.size) return null
    val reach = if (offset >= maxValue - POSITION_TOLERANCE) Float.MAX_VALUE else offset + POSITION_TOLERANCE
    return rows.stepSections[rows.stepOffsets.indexOfLast { it <= reach }.coerceAtLeast(0)]
}

/**
 * The sections a scroll at [offset] shows on a screen [viewport] long, by index: from the one it is headed by
 * ([sectionAt]) to the section of the last line starting on the screen where the song is a column of lines, else to the
 * last section of the last stop starting on it (a stop past the screen's end that starts in the middle of a section
 * leaves that section on the screen too; none past it, the song's last). Null before the song has been laid out.
 */
internal fun sectionsOnScreen(offset: Int, viewport: Int, rows: SongRows, maxValue: Int = Int.MAX_VALUE): IntRange? {
    val first = sectionAt(offset, rows, maxValue) ?: return null
    val end = offset + viewport - POSITION_TOLERANCE
    val last = if (rows.hasLines) {
        rows.lineSections.getOrNull(rows.lineTops.indexOfLast { it < end }) ?: first
    } else {
        when (val next = rows.stepOffsets.indexOfFirst { it >= end }) {
            -1 -> Int.MAX_VALUE
            0 -> first
            else -> maxOf(rows.stepSections[next - 1], rows.stepSections[next] - 1)
        }
    }
    return first..maxOf(first, last)
}
