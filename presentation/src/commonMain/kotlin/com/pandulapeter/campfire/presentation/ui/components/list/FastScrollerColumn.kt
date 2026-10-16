/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components.list

import com.pandulapeter.campfire.presentation.ui.components.ScrollPosition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull

/**
 * The width a [FastScroller]'s column takes from the end of its list: [FAST_SCROLLER_WIDTH] while the list scrolls, and
 * nothing while it fits, so a short list sits as far from the end edge as from the start one.
 *
 * Whether the list scrolls is only known once the grid has been measured, a frame after its first composition, so the
 * answer is kept in [position], which outlives the screen: a list returned to starts at the width it was left with.
 * The first answer after that is taken as it is, and so is every one while [isAnimated] is false (the library still
 * arriving), since neither is something the user did; any later one, a song added or the keyboard taking the height,
 * resizes the cards in an animation.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun fastScrollerColumnWidth(
    gridState: LazyGridState,
    position: ScrollPosition,
    isAnimated: Boolean,
): Dp {
    val width = remember(gridState, position) { Animatable(columnWidth(position.isScrollable), Dp.VectorConverter) }
    val currentIsAnimated by rememberUpdatedState(isAnimated)
    val animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Dp>()
    LaunchedEffect(gridState, position, width, animationSpec) {
        var isFirst = true
        snapshotFlow { if (gridState.layoutInfo.viewportSize.height == 0) null else gridState.canScrollForward || gridState.canScrollBackward }
            .filterNotNull()
            .distinctUntilChanged()
            .collectLatest { isScrollable ->
                position.isScrollable = isScrollable
                val isSnapped = isFirst || !currentIsAnimated
                isFirst = false
                if (isSnapped) width.snapTo(columnWidth(isScrollable)) else width.animateTo(columnWidth(isScrollable), animationSpec)
            }
    }
    // A spatial spring overshoots its target, which below zero would be a negative padding.
    return width.value.coerceAtLeast(0.dp)
}

private fun columnWidth(isScrollable: Boolean) = if (isScrollable) FAST_SCROLLER_WIDTH else 0.dp
