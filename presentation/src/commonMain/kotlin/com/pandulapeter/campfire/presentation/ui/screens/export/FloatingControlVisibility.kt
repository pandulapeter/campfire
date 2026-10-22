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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.offset
import androidx.compose.ui.unit.dp

/**
 * A control floating over the screen with a shadow of its own (a floating action button, the page buttons' pill), faded
 * and grown in while [isVisible] and faded and shrunk away again. Not `fadeIn` and `scaleIn`: a fade draws what it fades
 * into an offscreen buffer the size of its layer while it is not yet opaque, and their layer is the control's size, which
 * cut the shadow around it off until the fade was over. This layer reaches [SHADOW_ROOM] past the control on every side,
 * so the shadow fades with it, while the control still takes up only its own size in the layout, and none while gone.
 *
 * Where [isFirstShownAtOnce], its first appearance is not animated: a control the screen opened with is part of the
 * screen, and the pages it waits for are data arriving, not something the user did.
 */
@Composable
internal fun FloatingControlVisibility(
    modifier: Modifier,
    isVisible: Boolean,
    isFirstShownAtOnce: Boolean,
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
                        val shown = if (isShownAtOnce) 1f else progress.value
                        alpha = shown
                        scaleX = shown
                        scaleY = shown
                    }
                    .padding(SHADOW_ROOM),
            ) {
                content()
            }
        }
    }
}

/** How far past a floating control its shadow is drawn, which the layer fading it has to hold. */
private val SHADOW_ROOM = 16.dp
