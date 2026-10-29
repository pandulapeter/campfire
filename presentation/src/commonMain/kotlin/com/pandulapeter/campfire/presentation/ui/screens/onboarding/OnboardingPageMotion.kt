/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import com.pandulapeter.campfire.presentation.ui.chrome.endwardTranslation
import kotlin.math.abs

/**
 * How far the page reading it is from being the one shown, in pages: 0 while it is, growing towards 1 as it leaves to
 * the start or comes back from there, and towards -1 on the end's side. A function rather than a value, so that a turn
 * is read in the draw phase of the layers that follow it and never recomposes a page.
 */
internal val LocalOnboardingPageOffset = compositionLocalOf<() -> Float> { { 0f } }

/**
 * Where one layer of a page is drawn while it turns: moved sideways by [shift] page widths towards the end edge on top
 * of the pager's own move, scaled by [scale] around its center, and [alpha] opaque. The offset it is computed from is
 * logical, as the pager's pages are, so the shift is too; the right-to-left pager lays the next page out on the left.
 */
internal data class OnboardingLayerMotion(
    val shift: Float,
    val scale: Float,
    val alpha: Float,
)

/**
 * The question and the line under it at [offset]: lagging behind the page and fading, so that the next question seems
 * to settle in place of the last rather than slide by. Moved only sideways, never scaled, since the mark's place is in
 * this layer and the mark follows where that place is: it may only move up or down where the pages ask it to.
 */
internal fun onboardingHeaderMotion(offset: Float): OnboardingLayerMotion {
    val distance = abs(offset).coerceAtMost(1f)
    return OnboardingLayerMotion(
        shift = offset.coerceIn(-1f, 1f) * HEADER_LAG,
        scale = 1f,
        alpha = (1f - distance * HEADER_FADE_SPEED).coerceAtLeast(0f),
    )
}

/**
 * The answers at [offset]: falling back into the page as it leaves, and rising out of it as it comes, a little ahead of
 * the page, so that they turn as a layer of their own under the question.
 */
internal fun onboardingAnswersMotion(offset: Float): OnboardingLayerMotion {
    val distance = abs(offset).coerceAtMost(1f)
    return OnboardingLayerMotion(
        shift = -offset.coerceIn(-1f, 1f) * ANSWERS_LEAD,
        scale = 1f - distance * ANSWERS_DEPTH,
        alpha = 1f - distance,
    )
}

/** Draws this layer as [motion] says for the page offset it is composed under ([LocalOnboardingPageOffset]). */
@Composable
internal fun Modifier.onboardingLayerMotion(motion: (offset: Float) -> OnboardingLayerMotion): Modifier {
    val pageOffset = LocalOnboardingPageOffset.current
    val layoutDirection = LocalLayoutDirection.current
    return graphicsLayer {
        val layer = motion(pageOffset())
        translationX = endwardTranslation(distance = layer.shift * size.width, layoutDirection = layoutDirection)
        scaleX = layer.scale
        scaleY = layer.scale
        alpha = layer.alpha
    }
}

/** The share of the page's move the question holds back, in page widths. */
private const val HEADER_LAG = 0.4f

/** How much faster than the page the question fades: gone before the page is two thirds of the way out. */
private const val HEADER_FADE_SPEED = 1.5f

/** How far ahead of the page the answers move, in page widths. */
private const val ANSWERS_LEAD = 0.15f

/** How far the answers shrink back by the time their page is a whole width away. */
private const val ANSWERS_DEPTH = 0.2f
