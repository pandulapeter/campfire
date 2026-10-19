/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components.controls

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Two settings rows next to each other where both fit on one line unwrapped, [first] as wide as it needs and [second]
 * taking the rest, and [first] above [second] otherwise: the Chords and the Scales tabs' instrument and key. Each row
 * pads itself, so they need no gap of their own. Whether they fit is asked of the rows' widest intrinsic widths, which
 * a wrapping chip row gives as its chips on one line, so a key row is never wrapped to make room beside the instrument.
 *
 * A change between the two (a longer tuning's name, a narrower window) slides [second] to its new place while the
 * height follows; [second] takes its new width at once, so its chips do not rewrap on every frame of the way.
 */
@Composable
internal fun SideBySideWhenFits(
    modifier: Modifier = Modifier,
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val arrangement = remember { SideBySideArrangement() }
    Layout(
        modifier = modifier,
        contents = listOf(first, second),
    ) { (firstMeasurables, secondMeasurables), constraints ->
        val firstMeasurable = firstMeasurables.single()
        val secondMeasurable = secondMeasurables.single()
        val firstWidth = firstMeasurable.maxIntrinsicWidth(Constraints.Infinity)
        val fits = constraints.hasBoundedWidth &&
            firstWidth + secondMeasurable.maxIntrinsicWidth(Constraints.Infinity) <= constraints.maxWidth
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else constraints.minWidth
        val firstPlaceable = firstMeasurable.measure(if (fits) Constraints.fixedWidth(firstWidth) else Constraints(maxWidth = width))
        val secondPlaceable = secondMeasurable.measure(
            if (fits) Constraints.fixedWidth(width - firstWidth) else Constraints(maxWidth = width),
        )
        // Read here, the progress remeasures this layout alone on every frame of a change.
        val progress = arrangement.progressTowards(isSideBySide = fits, scope = scope, spec = spec)
        val stackedHeight = firstPlaceable.height + secondPlaceable.height
        val sideBySideHeight = maxOf(firstPlaceable.height, secondPlaceable.height)
        layout(width = width, height = lerp(stackedHeight, sideBySideHeight, progress)) {
            firstPlaceable.placeRelative(0, 0)
            secondPlaceable.placeRelative(lerp(0, firstWidth, progress), lerp(firstPlaceable.height, 0, progress))
        }
    }
}

/**
 * How far a [SideBySideWhenFits] is from stacked (0) to side by side (1). The first measure starts where it lands, so the
 * first frame is already right; every change after it is animated.
 */
private class SideBySideArrangement {

    private var progress: Animatable<Float, AnimationVector1D>? = null
    private var target = 0f

    fun progressTowards(isSideBySide: Boolean, scope: CoroutineScope, spec: AnimationSpec<Float>): Float {
        val newTarget = if (isSideBySide) 1f else 0f
        val progress = progress ?: Animatable(newTarget).also {
            this.progress = it
            target = newTarget
        }
        if (newTarget != target) {
            target = newTarget
            scope.launch { progress.animateTo(newTarget, spec) }
        }
        return progress.value
    }
}
