/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs.sheet

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
import androidx.compose.ui.unit.Density
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
 * new maximum on every frame of its own animation, or the window being resized) is followed at once where it bounds the
 * content, or the sheet would trail behind the keyboard and leave a gap over it. While the sheet is growing the content
 * is already measured at its new height and placed under the animated one, so the content is uncovered from the sheet's
 * bottom edge, which the sheet's shape clips, and how much of it is still covered is [SheetHeightAnimation.heightLag].
 *
 * The status bar's inset ([topInset], in pixels) is kept clear here rather than by Material, which pads the sheet by
 * the part of the inset its top edge has come into, read from the sheet's offset. That offset only follows the height
 * given here after it has been laid out, so such a padding arrives a frame late: a sheet growing to its tallest would
 * draw its header under the status bar for a frame, then push everything down by the inset. Here the content is never offered
 * more than the room below the inset, and content that fills that room makes the sheet take the whole room, its top
 * edge behind the status bar, with the content placed below the inset in the same pass. The room offered does not depend
 * on the sheet's offset either, so content filling the sheet keeps the same height at every offset of a drag, and the
 * expanded anchor stays where it is while the finger pulls the sheet down.
 */
@Composable
internal fun Modifier.animateSheetContentHeight(state: SheetHeightAnimation, topInset: Density.() -> Int): Modifier {
    val coroutineScope = rememberCoroutineScope()
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    return layout { measurable, constraints ->
        val clearRoom = if (constraints.hasBoundedHeight) (constraints.maxHeight - topInset()).coerceAtLeast(0) else constraints.maxHeight
        val placeable = measurable.measure(constraints.copy(minHeight = minOf(constraints.minHeight, clearRoom), maxHeight = clearRoom))
        val fillsTallestSheet = constraints.hasBoundedHeight && placeable.height >= clearRoom
        val targetHeight = if (fillsTallestSheet) constraints.maxHeight else placeable.height
        val animatable = state.animatable
        val snappedHeight = state.snappedHeight
        val isRoomChanged = constraints.maxHeight != state.maxHeight
        state.maxHeight = constraints.maxHeight
        // A change of the room is followed at once only where the room bounds the content, or where nothing is animating
        // anyway: a sheet shrinking to content that fits is not bounded by the room at all.
        val height = if (animatable == null || isRoomChanged && (fillsTallestSheet || !animatable.isRunning)) {
            // The first measure opens the sheet at its full height, which Material slides in on its own.
            if (animatable == null) {
                state.animatable = Animatable(targetHeight.toFloat())
            } else {
                // A snap is a coroutine that only lands after this frame, so the height is held here until it has.
                state.snappedHeight = targetHeight
                coroutineScope.launch { animatable.snapTo(targetHeight.toFloat()) }
            }
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
        // Only a sheet taller than the room below the inset reaches behind the status bar, and its content is kept below.
        val contentTop = (height - clearRoom).coerceAtLeast(0)
        state.heightLagState.intValue = placeable.height - (height - contentTop)
        layout(placeable.width, height) { placeable.placeRelative(0, contentTop) }
    }
}

/**
 * What [animateSheetContentHeight] animates, the maximum height it was offered last and the height it snapped to for
 * that, which is not state: it is only read and written by its measure pass. [heightLag] is, since the content reads
 * it as it is placed ([followSheetGrowth], `PreferencesFrame`), in the same frame.
 */
internal class SheetHeightAnimation {
    var animatable: Animatable<Float, AnimationVector1D>? = null
    var maxHeight = 0
    var snappedHeight: Int? = null
    val heightLagState = mutableIntStateOf(0)

    /**
     * How far the part of the sheet the content is shown in is behind the content's height: positive while it grows,
     * negative while it shrinks.
     *
     * The lag is written by the measure pass, and a placement that only read it would hear of the write a frame late,
     * one frame's step of the animation off the sheet's edge. Reading the animation's value too invalidates that
     * placement as the value is written, before layout, and the measure writing the lag (a parent's) runs before it.
     */
    fun heightLag(): Int {
        animatable?.value
        return heightLagState.intValue
    }
}

/** [SheetHeightAnimation.heightLag] of the [CampfireBottomSheet] around it, 0 while its height is not animating. */
internal val LocalSheetHeightLag = staticCompositionLocalOf<() -> Int> { { 0 } }

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
    val heightLag = LocalSheetHeightLag.current
    return clipToBounds().offset { IntOffset(0, -heightLag().coerceAtLeast(0)) }
}
