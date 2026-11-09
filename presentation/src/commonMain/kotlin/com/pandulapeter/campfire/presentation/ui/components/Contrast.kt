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

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/**
 * Whether [dark] reads more clearly on [background] than [light] does, by contrast as WCAG measures it: the geometric
 * midpoint of `luminance + 0.05` of the two is where they contrast with it equally, which for black and white is a
 * luminance of about 0.18 rather than the 0.5 the eye would guess.
 */
internal fun isDarkerMoreLegible(background: Color, light: Color = Color.White, dark: Color = Color.Black): Boolean =
    (background.luminance() + 0.05f).let { it * it } > (light.luminance() + 0.05f) * (dark.luminance() + 0.05f)

/**
 * [color] moved towards black or white, whichever reads on [background], only as far as it takes to contrast with it by
 * [minimumContrast] as WCAG measures it, so that it stays as much of its hue as it can. It is for a colour somebody else
 * picked without knowing the background, such as a song's `{chordcolour}`: a black chord on a dark theme, or a yellow
 * one on paper.
 */
internal fun legibleOn(background: Color, color: Color, minimumContrast: Float = MINIMUM_CONTRAST): Color {
    val pole = if (isDarkerMoreLegible(background)) Color.Black else Color.White
    var step = 0
    var result = color
    while (contrast(result, background) < minimumContrast && step < LEGIBILITY_STEPS) {
        step++
        result = lerp(color, pole, step.toFloat() / LEGIBILITY_STEPS)
    }
    return result
}

private fun contrast(first: Color, second: Color): Float {
    val firstLuminance = first.luminance() + 0.05f
    val secondLuminance = second.luminance() + 0.05f
    return maxOf(firstLuminance, secondLuminance) / minOf(firstLuminance, secondLuminance)
}

/** What WCAG asks of bold text, which a chord is, with some room left for the cards that are a tone off the surface. */
private const val MINIMUM_CONTRAST = 3.5f
private const val LEGIBILITY_STEPS = 20
