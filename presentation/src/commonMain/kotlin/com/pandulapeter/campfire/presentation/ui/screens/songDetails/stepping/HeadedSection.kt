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
 * section of the first line starting at or below it where its lines reach it ([SongRows.hasLines]: a column's, or a
 * row's too tall for the screen, which a song turned by row lists alone), else the section the stop at or above it
 * starts with, the first stop's at the top of the song. Null before the song has been laid out.
 */
internal fun sectionAt(offset: Int, rows: SongRows): Int? {
    val hasStops = rows.stepOffsets.isNotEmpty() && rows.stepSections.size == rows.stepOffsets.size
    if (rows.linesReach(offset) || (!hasStops && rows.hasLines)) {
        val line = rows.lineTops.indexOfFirst { it >= offset - POSITION_TOLERANCE }
        return rows.lineSections.getOrNull(if (line < 0) rows.lineTops.lastIndex else line)
    }
    if (!hasStops) return null
    // The song opens on its first stop. The columns of a song that fits a single row are stepped by section, each
    // column's first clamped to the top too, so the last of the stops there would name the last column instead.
    if (offset <= POSITION_TOLERANCE) return rows.stepSections.first()
    return rows.stepSections[rows.stepOffsets.indexOfLast { it <= offset + POSITION_TOLERANCE }.coerceAtLeast(0)]
}

/**
 * The sections a scroll at [offset] shows on a screen [viewport] long, by index: from the one it is headed by
 * ([sectionAt]) to the section of the last line starting on the screen where its lines reach the screen's end, else to
 * the last section of the last stop starting on it (a stop past the screen's end that starts in the middle of a section
 * leaves that section on the screen too; none past it, the song's last), and to the song's last wherever the scroll is
 * at its end, [maxValue]: a song that fits the screen shows every section. Null before the song has been laid out.
 */
internal fun sectionsOnScreen(offset: Int, viewport: Int, rows: SongRows, maxValue: Int = Int.MAX_VALUE): IntRange? {
    val first = sectionAt(offset, rows) ?: return null
    val end = offset + viewport - POSITION_TOLERANCE
    val last = when {
        offset >= maxValue - POSITION_TOLERANCE -> Int.MAX_VALUE
        rows.linesReach(end) || (rows.stepOffsets.isEmpty() && rows.hasLines) -> rows.lineSections.getOrNull(rows.lineTops.indexOfLast { it < end }) ?: first
        else -> when (val next = rows.stepOffsets.indexOfFirst { it >= end }) {
            -1 -> Int.MAX_VALUE
            0 -> first
            else -> maxOf(rows.stepSections[next - 1], rows.stepSections[next] - 1)
        }
    }
    return first..maxOf(first, last)
}

/**
 * Whether the song's lines reach down to [offset]: a column's always, while a song turned by row lists only the lines of
 * a row too tall for the screen, which say nothing about the rows above it.
 */
private fun SongRows.linesReach(offset: Number) = hasLines && offset.toFloat() >= lineTops.first() - POSITION_TOLERANCE
