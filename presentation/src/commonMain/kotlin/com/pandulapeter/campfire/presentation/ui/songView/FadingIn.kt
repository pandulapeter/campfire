/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songView

import androidx.compose.animation.core.Animatable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Fades a run of tablature, a grid or the song's info card in as it is unfolded, while the section around it grows to
 * make room on its own spring (`animateBounds`). Only the opacity is animated, never the size: the column layout decides where every
 * section goes from their intrinsic heights, and a run whose height was still on its way would be measured halfway
 * there.
 */
@Composable
internal fun Modifier.fadingIn(isFadingIn: Boolean): Modifier {
    val alpha = remember { Animatable(if (isFadingIn) 0f else 1f) }
    val spec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    LaunchedEffect(alpha) { alpha.animateTo(1f, spec) }
    return graphicsLayer { this.alpha = alpha.value }
}
