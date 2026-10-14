/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songView.firstSection

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Where each of a layout's children is drawn while it travels, by the child's key ([BalancedRows]' items, a
 * [PlayingControl]'s label and control). The animations are started from the placement, which is the one place the new
 * positions are known, and read there too, so a frame of the travel only places the items again rather than measuring
 * them.
 */
internal class PlacementMotion(private val coroutineScope: CoroutineScope) {

    private val offsets = mutableMapOf<Any, Animatable<IntOffset, *>>()

    fun Placeable.PlacementScope.place(placeable: Placeable, key: Any, target: IntOffset) {
        val offset = offsets.getOrPut(key) { Animatable(target, IntOffset.VectorConverter) }
        if (offset.targetValue != target) {
            coroutineScope.launch {
                offset.animateTo(
                    targetValue = target,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset.VisibilityThreshold),
                )
            }
        }
        placeable.placeRelative(offset.value)
    }

    /** Forgets every item that is no longer among [keys], so that one coming back is placed rather than sent travelling. */
    fun retain(keys: Set<Any>) {
        offsets.keys.retainAll(keys)
    }

    /**
     * Forgets every item, while the layout is not animated: a change that keeps coming (a pinch, a window edge dragged) is
     * followed frame by frame, and a travel that started before it would otherwise go on from a place the item has left.
     */
    fun clear() = offsets.clear()
}
