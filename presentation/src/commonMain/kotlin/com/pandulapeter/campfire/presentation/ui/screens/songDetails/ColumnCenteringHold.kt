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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/**
 * Where a column of a page spread out over it starts ([top], below the top of its page) and how much its section gaps
 * grew by ([extraGap]), see [SectionGrid.arrange].
 */
internal class ColumnSpread(
    val top: Int,
    val extraGap: Int,
)

/** The [ColumnSpread] of every column of a song, by [columnKey]. */
internal typealias ColumnSpreads = Map<Long, ColumnSpread>

/** The key a column is found by in [ColumnSpreads]: its row and its place in it. */
internal fun columnKey(row: Int, column: Int) = row.toLong() shl Int.SIZE_BITS or column.toLong()

/**
 * Keeps every column where it was for a moment after a fold is toggled, rather than centering it in its page again at
 * once: a column is centered by its height, so a fold would otherwise move the sections above it too, the very header
 * that was tapped among them, and a second tap aimed at it would miss. Once [isHolding] turns false the columns are
 * centered again, which their sections spring to like any other change. [spreads] is what the layout last placed them
 * at while nothing held them, with the [columnCounts] of the rows it was placed in.
 */
internal class ColumnCenteringHold {

    var isHolding by mutableStateOf(false)

    var spreads: ColumnSpreads = emptyMap()

    var columnCounts = IntArray(0)

    /** The spreads to place the columns of rows of [columnCounts] at, or null where they are centered as they are. */
    fun heldSpreads(columnCounts: IntArray) = spreads.takeIf { isHolding && this.columnCounts.contentEquals(columnCounts) }
}

/**
 * A [ColumnCenteringHold] that holds the columns for [FOLD_RECENTER_DELAY_MILLIS] whenever one of [foldKeys] changes -
 * the folds of the song, and of its Chords section - but not as the song is first composed.
 */
@Composable
internal fun rememberColumnCenteringHold(vararg foldKeys: Any?): ColumnCenteringHold {
    val hold = remember { ColumnCenteringHold() }
    val hasStarted = remember { booleanArrayOf(false) }
    LaunchedEffect(*foldKeys) {
        if (!hasStarted[0]) {
            hasStarted[0] = true
            return@LaunchedEffect
        }
        hold.isHolding = true
        try {
            delay(FOLD_RECENTER_DELAY_MILLIS)
        } finally {
            hold.isHolding = false
        }
    }
    return hold
}

/** How long the columns stay where they were after a fold: past its own animation, and past a second tap on its header. */
private const val FOLD_RECENTER_DELAY_MILLIS = 900L
