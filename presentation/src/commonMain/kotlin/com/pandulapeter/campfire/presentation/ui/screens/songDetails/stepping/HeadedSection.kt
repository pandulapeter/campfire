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
