/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import com.pandulapeter.campfire.presentation.ui.components.only
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Grows and shrinks a sheet to its content's new height rather than in one frame, when rows are added to a list in it or
 * taken away, a field's error appears, or the content is swapped for another. The sheet's top edge follows the height,
 * since Material anchors the sheet by it.
 *
 * Only the content's own changes are animated. A change of the room offered (the keyboard sliding, which arrives as a
 * new maximum on every frame of its own animation, or the window being resized) is followed at once, or the sheet
 * would trail behind the keyboard and leave a gap over it. While the sheet is growing the content is already measured
 * at its new height and placed under the animated one, so the content is uncovered from the sheet's bottom edge, which
 * the sheet's shape clips, and how much of it is still covered is [SheetHeightAnimation.pendingGrowth].
 *
 * Content that fills the sheet at its tallest keeps filling the room offered at every offset, whether or not it would
 * still need all of it. Material anchors the expanded sheet at the window's height less the sheet's, and the sheet's
 * height includes the part of the top inset it is still padded by, which shrinks as the sheet is dragged down off the
 * status bar ([uncoveredTopInset]). Content that overflows by less than that inset comes to fit part of the way down;
 * measured at its own height there, it would make the sheet shorter than the window, move the expanded anchor with every
 * frame of the drag and put the sheet back at the top over and over while the finger is still pulling it.
 */
@Composable
internal fun Modifier.animateSheetContentHeight(state: SheetHeightAnimation, uncoveredTopInset: () -> Dp): Modifier {
    val coroutineScope = rememberCoroutineScope()
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    return layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val fillsTallestSheet = constraints.hasBoundedHeight && placeable.height >= constraints.maxHeight - uncoveredTopInset().roundToPx()
        val targetHeight = if (fillsTallestSheet) constraints.maxHeight else placeable.height
        val animatable = state.animatable
        val snappedHeight = state.snappedHeight
        val height = if (animatable == null || constraints.maxHeight != state.maxHeight) {
            // The first measure opens the sheet at its full height, which Material slides in on its own.
            if (animatable == null) {
                state.animatable = Animatable(targetHeight.toFloat())
            } else {
                // A snap is a coroutine that only lands after this frame, so the height is held here until it has.
                state.snappedHeight = targetHeight
                coroutineScope.launch { animatable.snapTo(targetHeight.toFloat()) }
            }
            state.maxHeight = constraints.maxHeight
            targetHeight
        } else if (snappedHeight == targetHeight && animatable.value.roundToInt() != snappedHeight) {
            snappedHeight
        } else {
            state.snappedHeight = null
            if (animatable.targetValue != targetHeight.toFloat()) {
                coroutineScope.launch { animatable.animateTo(targetHeight.toFloat(), spec) }
            }
            animatable.value.roundToInt().coerceIn(constraints.minHeight, constraints.maxHeight)
        }
        state.pendingGrowthState.intValue = (targetHeight - height).coerceAtLeast(0)
        layout(placeable.width, height) { placeable.placeRelative(0, 0) }
    }
}

/**
 * What [animateSheetContentHeight] animates, the maximum height it was offered last and the height it snapped to for
 * that, which is not state: it is only read and written by its measure pass. [pendingGrowth] is, since the content
 * reads it as it is placed ([followSheetGrowth]), in the same frame.
 */
internal class SheetHeightAnimation {
    var animatable: Animatable<Float, AnimationVector1D>? = null
    var maxHeight = 0
    var snappedHeight: Int? = null
    val pendingGrowthState = mutableIntStateOf(0)

    fun pendingGrowth() = pendingGrowthState.intValue
}

/**
 * How many pixels the [CampfireBottomSheet] around it still has to grow to its content's height, 0 while it is not
 * growing.
 */
internal val LocalSheetPendingGrowth = staticCompositionLocalOf<() -> Int> { { 0 } }

/**
 * Keeps the rows of a list in a growing [CampfireBottomSheet] where they are on screen. The sheet's top edge rises as it
 * grows, and the list, measured at its new height already and hanging from that edge, would carry the rows below a
 * newly inserted one a row lower than where they end up and bring them back up as the sheet grows - away from under the
 * finger that ticked the row and back. Shifted up by what the sheet still has to grow, inside the list's own bounds,
 * the rows stay put and what was inserted slides in from under the controls above the list, as it would in a list
 * already scrolled to its end.
 *
 * Goes after the list's edge fades, so that they stay at the edges of its bounds rather than travel with the rows.
 */
@Composable
internal fun Modifier.followSheetGrowth(): Modifier {
    val pendingGrowth = LocalSheetPendingGrowth.current
    return clipToBounds().offset { IntOffset(0, -pendingGrowth()) }
}
