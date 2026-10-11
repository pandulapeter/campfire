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

/**
 * Whether a song whose units hold [lineCount] lines of lyrics each, and wrap into [wrappedLines] line heights more each
 * at the width of a column narrower than a readable one, fits such a column well enough to be set in it: no more than a
 * quarter of its lines may wrap there. A song of short lines reads as well in a narrow column as in a wide one, while
 * one whose every line is a sentence would be read in halves.
 */
internal fun fitsNarrowColumns(lineCount: IntArray, wrappedLines: (unit: Int) -> Int): Boolean {
    var lines = 0
    var wrappingLines = 0
    for (unit in lineCount.indices) {
        if (lineCount[unit] == 0) continue
        lines += lineCount[unit]
        wrappingLines += minOf(lineCount[unit], wrappedLines(unit))
    }
    return wrappingLines * NARROW_WRAPPING_SHARE <= lines
}

/**
 * The grid a stretch of the song is set in, of the best one in readable columns, [readable], and the ones in columns
 * narrower than that, [narrow]: the best of those only where it takes strictly fewer pages - never on a tie, however
 * much better it scores, since a narrow column is only worth what it saves.
 */
internal fun withNarrowColumns(readable: ScoredGrid, narrow: List<ScoredGrid>): ScoredGrid {
    var best: ScoredGrid? = null
    narrow.forEach { if (it.isBetterThan(best)) best = it }
    return best?.takeIf { it.pageCount < readable.pageCount } ?: readable
}

/** One in how many lines of lyrics may wrap for a song to be set in narrow columns. */
private const val NARROW_WRAPPING_SHARE = 4
