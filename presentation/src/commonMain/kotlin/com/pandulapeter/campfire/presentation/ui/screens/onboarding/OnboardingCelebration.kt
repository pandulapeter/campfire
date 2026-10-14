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

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.ui.theme.LocalSecondAccentColor
import kotlin.random.Random

/**
 * Embers sent up over the whole window as the onboarding is finished (`Navigator.isOnboardingFinishCelebrated`), the
 * way a campfire throws sparks when a log settles, while the onboarding zooms away under them. Only in the colors the
 * user just picked (see [celebrationColors]). Drawn over the screens but taking no input, so the app under them can be
 * used at once; [onFinished] is called once they have all burnt out.
 */
@Composable
internal fun OnboardingCelebration(
    modifier: Modifier = Modifier,
    isCelebrating: Boolean,
    onFinished: () -> Unit,
) {
    if (!isCelebrating) return
    val colors = celebrationColors(
        primary = MaterialTheme.colorScheme.primary,
        secondAccent = LocalSecondAccentColor.current,
        inversePrimary = MaterialTheme.colorScheme.inversePrimary,
    )
    val embers = remember { onboardingEmbers(random = Random.Default, count = EMBER_COUNT, colorCount = colors.size) }
    val progress = remember { Animatable(0f) }
    val currentOnFinished by rememberUpdatedState(onFinished)
    LaunchedEffect(progress) {
        progress.animateTo(1f, tween(ONBOARDING_CELEBRATION_MILLIS, easing = LinearEasing))
        currentOnFinished()
    }
    Canvas(
        modifier = modifier.fillMaxSize(),
    ) {
        embers.forEach { ember ->
            val frame = ember.frameAt(progress.value) ?: return@forEach
            val color = colors[ember.colorIndex]
            val center = Offset(x = frame.x * size.width, y = frame.y * size.height)
            val radius = ember.radius.dp.toPx() * frame.scale
            drawCircle(color = color, radius = radius * EMBER_GLOW_SCALE, center = center, alpha = frame.alpha * EMBER_GLOW_ALPHA)
            drawCircle(color = color, radius = radius, center = center, alpha = frame.alpha)
        }
    }
}

/**
 * The two colors the embers burn in: the palette's primary and its second accent, which is the purple and the orange of
 * the app's own palette. Every other palette has one color, its second accent being its primary, so its embers burn in
 * two tones of it instead (the primary and the inverse primary, its light and dark tones), which reads as a fire's
 * hotter and cooler sparks rather than as a second hue the user never picked. Always two, so the embers, laid out
 * once with a color index each, stay valid through a theme change.
 */
internal fun celebrationColors(primary: Color, secondAccent: Color, inversePrimary: Color) =
    listOf(primary, if (secondAccent == primary) inversePrimary else secondAccent)

private const val EMBER_COUNT = 90
private const val EMBER_GLOW_SCALE = 3f
private const val EMBER_GLOW_ALPHA = 0.25f
private const val ONBOARDING_CELEBRATION_MILLIS = 2000
