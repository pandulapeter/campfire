/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt

/**
 * An item of the navigation chrome that comes and goes with the feature it belongs to, see
 * `CampfireViewModel.topLevelDestinations`. It is composed for as long as it is shown or still leaving, and [content]
 * is handed how much of it is there, from 0 to 1, to give its slot with [collapsingNavigationItem]. One the chrome is
 * first composed without is never composed at all, and one it is first composed with is there from the first frame,
 * since only a switch flipped is a change the user caused.
 */
@Composable
internal fun NavigationItemPresence(
    isShown: Boolean,
    content: @Composable (presence: () -> Float) -> Unit,
) {
    val state = remember { MutableTransitionState(isShown) }
    state.targetState = isShown
    if (state.currentState || state.targetState) {
        val presence = rememberTransition(state).animateFloat(
            transitionSpec = { MaterialTheme.motionScheme.defaultEffectsSpec() },
        ) { if (it) 1f else 0f }
        content { presence.value }
    }
}

/**
 * Shrinks an item of the navigation chrome into nothing along the axis the chrome lays its items out on, fading it on
 * the way, so that the items beside it close the gap rather than jumping into it. What is inside is laid out at its own
 * size throughout and clipped to the shrinking slot: a label measured into a sliver of a slot would wrap a letter per
 * line and push the item out of the bar.
 *
 * @param isHorizontal True for the navigation bar, whose slots are fixed widths that the bar hands out by weight, so
 * the shrinking itself is the item's weight there and this only lets its content keep its width; false for the rails,
 * whose items are stacked at their own heights, which this scales.
 */
internal fun Modifier.collapsingNavigationItem(presence: () -> Float, isHorizontal: Boolean) = this
    .graphicsLayer { alpha = presence().coerceIn(0f, 1f) }
    .clipToBounds()
    .layout { measurable, constraints ->
        val fraction = presence().coerceIn(0f, 1f)
        when {
            fraction >= 1f -> measurable.measure(constraints).let { placeable ->
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            }

            isHorizontal -> measurable.measure(constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity)).let { placeable ->
                layout(constraints.maxWidth, placeable.height) { placeable.place((constraints.maxWidth - placeable.width) / 2, 0) }
            }

            else -> measurable.measure(constraints).let { placeable ->
                val height = (placeable.height * fraction).roundToInt()
                layout(placeable.width, height) { placeable.place(0, (height - placeable.height) / 2) }
            }
        }
    }
