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
import androidx.compose.runtime.SideEffect
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
 * at while nothing held them, and [pageShifts] how far it moved the pages of several rows down, with the
 * [columnCounts] and [joinsPrevious] of the grid it placed them in: a grid paired into other pages has other columns.
 */
internal class ColumnCenteringHold {

    var isHolding by mutableStateOf(false)

    var spreads: ColumnSpreads = emptyMap()

    var pageShifts: Map<Int, Int> = emptyMap()

    var columnCounts = IntArray(0)

    var joinsPrevious = BooleanArray(0)

    /**
     * The spreads to place the columns of a grid of [columnCounts] and [joinsPrevious] at, or null where they are centered
     * as they are.
     */
    fun heldSpreads(columnCounts: IntArray, joinsPrevious: BooleanArray) = spreads.takeIf { isHolding(columnCounts, joinsPrevious) }

    /**
     * How far to move the pages of several rows of a grid of [columnCounts] and [joinsPrevious] down, or null where they
     * are centered as they are.
     */
    fun heldPageShifts(columnCounts: IntArray, joinsPrevious: BooleanArray) = pageShifts.takeIf { isHolding(columnCounts, joinsPrevious) }

    private fun isHolding(columnCounts: IntArray, joinsPrevious: BooleanArray) =
        isHolding && this.columnCounts.contentEquals(columnCounts) && this.joinsPrevious.contentEquals(joinsPrevious)
}

/**
 * A [ColumnCenteringHold] that holds the columns for [FOLD_RECENTER_DELAY_MILLIS] whenever one of [foldKeys] changes -
 * the folds of the song, and of its Chords section - but not as the song is first composed.
 *
 * The hold is armed in a [SideEffect], as the composition that changed the keys is applied, because that always comes
 * before the layout of the same frame: a [LaunchedEffect] body is only dispatched then, and on the `ComposeScene`
 * platforms it runs after that frame has already measured, centered and recorded the folded columns. The coroutine
 * only releases it, and a second fold cancels it before its delay returns, so the hold lasts until 900 ms after the
 * last fold.
 */
@Composable
internal fun rememberColumnCenteringHold(vararg foldKeys: Any?): ColumnCenteringHold {
    val hold = remember { ColumnCenteringHold() }
    val watch = remember { FoldKeyWatch() }
    SideEffect {
        if (watch.hasChanged(foldKeys.toList())) {
            hold.isHolding = true
        }
    }
    LaunchedEffect(*foldKeys) {
        delay(FOLD_RECENTER_DELAY_MILLIS)
        hold.isHolding = false
    }
    return hold
}

/**
 * Tells whether the keys it is handed differ from the ones it was handed before, and never for the first ones. It
 * compares them by the same `equals` as [LaunchedEffect] compares its keys, so the hold is armed exactly when the effect
 * that releases it restarts.
 */
internal class FoldKeyWatch {

    private var previous: List<Any?>? = null

    fun hasChanged(keys: List<Any?>): Boolean {
        val isChanged = previous != null && previous != keys
        previous = keys
        return isChanged
    }
}

/** How long the columns stay where they were after a fold: past its own animation, and past a second tap on its header. */
private const val FOLD_RECENTER_DELAY_MILLIS = 900L
