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
 * Fades in what a single change brings onto a song page - a run of tablature, a grid or the song's info card as it is
 * unfolded, a section that was not there - while the sections around it glide to make room ([SectionMotion.SPRING]).
 * Only the opacity is animated, never the size: the column layout decides where every section goes from their
 * intrinsic heights, and a run whose height was still on its way would be measured halfway there.
 *
 * Whether it fades is decided on the first composition, and nearly nothing on a song does (never on a page's first
 * frame), so what does not fade gets no animation, no effect, and no layer of its own. [keepsLayer] keeps the layer
 * anyway, for a chunk and a folded run: a chunk is placed without a layer, and its own keeps its drawing (a staff, a
 * tab's frets) from being recorded again on every frame of a glide; a run's keeps a tab's or a grid's drawing from being
 * recorded again on every frame something else in its chunk redraws, a chord name cross-fading after a transposition.
 */
@Composable
internal fun Modifier.fadingIn(
    isFadingIn: Boolean,
    keepsLayer: Boolean = false,
): Modifier {
    val fades = remember { isFadingIn }
    if (!fades) return if (keepsLayer) graphicsLayer() else this
    val alpha = remember { Animatable(0f) }
    val spec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    LaunchedEffect(alpha) { alpha.animateTo(1f, spec) }
    return graphicsLayer { this.alpha = alpha.value }
}
