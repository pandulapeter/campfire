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

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.round
import kotlinx.coroutines.launch

/**
 * Carries a child from where its parent last placed it to where the parent places it now, on [animationSpec], rather
 * than letting it jump there: a chip that a narrower window sends to the next line of a `FlowRow`, or one that a
 * neighbour grown wider pushes along. Only a move inside the parent travels - the parent moving with its own parent (a
 * scroll, a section going to another column) leaves the child where it is in it - so the children of a row that stand
 * at its start follow a window edge dragged frame by frame, and travel only where one of them changes lines.
 *
 * It must come first in the child's modifiers, since what it compares is where the parent puts the child. Not for the
 * items of a lazy list, whose reused items would set off from where the previous item had its children.
 */
internal fun Modifier.animatePlacement(animationSpec: FiniteAnimationSpec<IntOffset>): Modifier = this then PlacementAnimationElement(animationSpec)

private data class PlacementAnimationElement(
    val animationSpec: FiniteAnimationSpec<IntOffset>,
) : ModifierNodeElement<PlacementAnimationNode>() {

    override fun create() = PlacementAnimationNode(animationSpec)

    override fun update(node: PlacementAnimationNode) {
        node.animationSpec = animationSpec
    }
}

private class PlacementAnimationNode(
    var animationSpec: FiniteAnimationSpec<IntOffset>,
) : Modifier.Node(), LayoutModifierNode {

    private var position: Animatable<IntOffset, AnimationVector2D>? = null

    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val placeable = measurable.measure(constraints)
        // The lookahead pass is where the layout is headed, so it is placed there at once.
        val isLookingAhead = isLookingAhead
        return layout(placeable.width, placeable.height) {
            // Reading the coordinates while placing is also what has this placement run again whenever the parent moves
            // the child without measuring it anew, which is the move this is here for.
            val target = coordinates?.takeUnless { isLookingAhead }?.positionInParent()?.round()
            if (target == null) {
                placeable.place(IntOffset.Zero)
            } else {
                val position = position ?: Animatable(target, IntOffset.VectorConverter, IntOffset.VisibilityThreshold).also { position = it }
                if (position.targetValue != target) {
                    coroutineScope.launch { position.animateTo(targetValue = target, animationSpec = animationSpec) }
                }
                placeable.place(position.value - target)
            }
        }
    }

    /** A child attached again is placed rather than sent travelling from wherever it was before it left. */
    override fun onDetach() {
        position = null
    }
}
