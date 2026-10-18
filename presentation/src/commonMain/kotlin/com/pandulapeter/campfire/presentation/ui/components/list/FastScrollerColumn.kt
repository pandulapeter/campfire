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
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * The column a [FastScroller] takes from the end of its list: [FAST_SCROLLER_WIDTH] wide, with the thumb shown, while
 * the list scrolls, and nothing while it fits, so a short list sits as far from the end edge as from the start one.
 *
 * Whether the list scrolls is only known once the grid has been measured, a frame after its first composition, so the
 * answer is kept in [position], which outlives the screen: a list returned to starts at the width it was left with.
 * The first answer after that is taken as it is, and so is every one measured from a composition that was not
 * animated (the library still arriving), since neither is something the user did; any later one, a song added or the
 * keyboard taking the height, resizes the cards and fades the thumb in an animation.
 */
@Stable
internal class FastScrollerColumn(
    private val position: ScrollPosition,
) {
    private val widthAnimatable = Animatable(columnWidth(position.isScrollable), Dp.VectorConverter)
    private var lastIsScrollable: Boolean? = null
    private val changes = Channel<Change>(Channel.UNLIMITED)

    /** A spatial spring overshoots its target, which below zero would be a negative padding. */
    val width: Dp get() = widthAnimatable.value.coerceAtLeast(0.dp)

    val thumbAlpha = Animatable(if (position.isScrollable) 1f else 0f)

    /**
     * Called right after the grid is measured, while [isAnimated] is still the one of the composition that gave the
     * grid its content. A coroutine watching the grid would hear about the library's last batch only after the screen
     * had recomposed as loaded, and so animate the cards squeezing in as the app opens.
     */
    fun onGridMeasured(gridState: LazyGridState, isAnimated: Boolean) {
        if (gridState.layoutInfo.viewportSize.height == 0) return
        val isScrollable = gridState.canScrollForward || gridState.canScrollBackward
        val last = lastIsScrollable
        if (isScrollable == last) return
        lastIsScrollable = isScrollable
        position.isScrollable = isScrollable
        changes.trySend(Change(isScrollable = isScrollable, isAnimated = last != null && isAnimated))
    }

    suspend fun followChanges(widthSpec: AnimationSpec<Dp>, alphaSpec: AnimationSpec<Float>) {
        changes.receiveAsFlow().collectLatest { change ->
            val width = columnWidth(change.isScrollable)
            val alpha = if (change.isScrollable) 1f else 0f
            if (change.isAnimated) {
                coroutineScope {
                    launch { widthAnimatable.animateTo(width, widthSpec) }
                    launch { thumbAlpha.animateTo(alpha, alphaSpec) }
                }
            } else {
                widthAnimatable.snapTo(width)
                thumbAlpha.snapTo(alpha)
            }
        }
    }

    private class Change(
        val isScrollable: Boolean,
        val isAnimated: Boolean,
    )
}

/**
 * The [FastScrollerColumn] of [gridState]'s list, starting from what [position] remembers. The grid tells it whether it
 * scrolls through [measuresFastScrollerColumn].
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun rememberFastScrollerColumn(
    gridState: LazyGridState,
    position: ScrollPosition,
): FastScrollerColumn {
    val column = remember(gridState, position) { FastScrollerColumn(position) }
    val widthSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Dp>()
    val alphaSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    LaunchedEffect(column, widthSpec, alphaSpec) { column.followChanges(widthSpec, alphaSpec) }
    return column
}

/** Tells [column] whether the grid it is applied to scrolls; [isAnimated] is false while the library is still arriving. */
internal fun Modifier.measuresFastScrollerColumn(
    column: FastScrollerColumn,
    gridState: LazyGridState,
    isAnimated: Boolean,
) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    // The grid writes its layout info on every measure of this same node, so a read observed here would ask for the node
    // to be measured again after every measure, and the list would never settle. The node is measured whenever the grid
    // is, so nothing is missed.
    Snapshot.withoutReadObservation { column.onGridMeasured(gridState, isAnimated) }
    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
}

private fun columnWidth(isScrollable: Boolean) = if (isScrollable) FAST_SCROLLER_WIDTH else 0.dp
