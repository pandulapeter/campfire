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

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A control floating over the screen with a shadow (a floating action button, the page buttons' pill), faded and grown in
 * while [isVisible] and faded and shrunk away again. Not `fadeIn` and `scaleIn`, whose fade draws the control into an
 * offscreen buffer the size of its layer until it is opaque, cutting off the shadow around it, the further the lower on
 * the screen the control is, since the light that casts it is above the window. Here the fade is applied to each thing
 * drawn instead (`ModulateAlpha`), and the shadow is drawn around [content] by this layer itself, in [shape], with
 * [elevation] growing and shrinking with the fade, so that whatever a platform does with a shadow under a fade, it never
 * shows up late or alone: the control draws none of its own. The layer also reaches [SHADOW_ROOM] past the control on
 * every side, for a platform that does draw a buffer, while the control takes up only its own size in the layout.
 *
 * Where [isFirstShownAtOnce], its first appearance is not animated: a control the screen opened with is part of the
 * screen, and the pages it waits for are data arriving, not something the user did.
 */
@Composable
internal fun FloatingControlVisibility(
    modifier: Modifier,
    isVisible: Boolean,
    isFirstShownAtOnce: Boolean,
    shape: Shape,
    elevation: Dp,
    content: @Composable () -> Unit,
) {
    val progress = remember { Animatable(0f) }
    var hasBeenShown by remember { mutableStateOf(false) }
    // Read in the composition as well as by the effect, so that a control shown at once is drawn whole in its first frame
    // rather than in the frame after the effect snaps it there.
    val isShownAtOnce = isVisible && !hasBeenShown && isFirstShownAtOnce
    LaunchedEffect(isVisible) {
        // Snapped before the control counts as shown, which ends the composition's own reading of it.
        if (isShownAtOnce) progress.snapTo(1f)
        if (isVisible) hasBeenShown = true
        if (!isShownAtOnce) progress.animateTo(if (isVisible) 1f else 0f, spring(stiffness = Spring.StiffnessMediumLow))
    }
    val shown = { if (isShownAtOnce) 1f else progress.value }
    Box(modifier) {
        if (isVisible || progress.value > 0f) {
            Box(
                Modifier
                    .layout { measurable, constraints ->
                        val room = SHADOW_ROOM.roundToPx()
                        val placeable = measurable.measure(constraints.offset(horizontal = 2 * room, vertical = 2 * room))
                        layout(placeable.width - 2 * room, placeable.height - 2 * room) { placeable.place(-room, -room) }
                    }
                    .graphicsLayer {
                        alpha = shown()
                        scaleX = shown()
                        scaleY = shown()
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                    }
                    .padding(SHADOW_ROOM)
                    .graphicsLayer {
                        shadowElevation = elevation.toPx() * shown()
                        this.shape = shape
                        clip = false
                    },
            ) {
                content()
            }
        }
    }
}

/** How far past a floating control its shadow is drawn, which the layer fading it has to hold. */
private val SHADOW_ROOM = 16.dp
