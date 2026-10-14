/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songView.grid

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
