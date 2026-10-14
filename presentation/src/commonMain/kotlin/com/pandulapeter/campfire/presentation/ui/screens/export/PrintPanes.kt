/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.export

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.animateBounds
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.unit.Dp

/**
 * The options beside the preview, [optionsWidth] wide at its start, or filling the width where that is null,
 * with the preview as their first scrolling item. The options keep their scroll across arrangements and their bounds
 * animate when the arrangement changes, while a resize within one arrangement is followed as it comes.
 */
@Composable
internal fun PrintPanes(
    optionsWidth: Dp?,
    options: @Composable (Modifier) -> Unit,
    preview: @Composable (Modifier) -> Unit,
) {
    val arrangement = updateTransition(targetState = optionsWidth, label = "PrintPanes")
    val motionScheme = MaterialTheme.motionScheme
    // A child animation on the same spring keeps the transition running for as long as the panes travel, which is what
    // tells a change of arrangement from a resize in the frames after the first one.
    arrangement.animateFloat(transitionSpec = { motionScheme.defaultSpatialSpec() }, label = "PrintPanesProgress") {
        it?.value ?: 0f
    }
    val spec = motionScheme.paneBoundsSpec()
    val boundsTransform = remember(arrangement, spec) {
        BoundsTransform { _, _ -> if (arrangement.currentState != arrangement.targetState) spec else snap() }
    }
    LookaheadScope {
        val paneBounds = Modifier.animateBounds(lookaheadScope = this, boundsTransform = boundsTransform)
        Box(Modifier.fillMaxSize()) {
            options(
                if (optionsWidth != null) {
                    Modifier.width(optionsWidth).fillMaxHeight()
                } else {
                    Modifier.fillMaxSize()
                }.then(paneBounds),
            )
            if (optionsWidth != null) {
                preview(Modifier.fillMaxSize().padding(start = optionsWidth).then(paneBounds))
            }
        }
    }
}

/** The motion scheme's spatial spring for the bounds of a pane, settling within a pixel. */
private fun MotionScheme.paneBoundsSpec() = when (val spec = defaultSpatialSpec<Rect>()) {
    is SpringSpec -> spring(dampingRatio = spec.dampingRatio, stiffness = spec.stiffness, visibilityThreshold = Rect.VisibilityThreshold)
    else -> spec
}
