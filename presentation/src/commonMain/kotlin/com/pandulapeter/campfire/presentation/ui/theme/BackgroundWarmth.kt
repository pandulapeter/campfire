/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import kotlin.math.PI
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The palette with its neutral roles - the backgrounds and surfaces, and the text, outlines and inverse surfaces drawn
 * with them - turned towards the warm brown of old paper by [level] steps out of
 * [UserPreferences.MAX_BACKGROUND_WARMTH], in both halves, and the light half dimmed as a whole on the way.
 *
 * What keeps this from costing contrast is that a WCAG contrast ratio is `(Y1 + 0.05) / (Y2 + 0.05)` of the two
 * relative luminances and nothing else, so two changes leave every ratio of the palette where it was:
 * - The sepia moves only the hue and the chroma of a neutral, in CIELAB, at its own L*, which is a function of relative
 *   luminance alone. A color it would take out of sRGB loses chroma at the same lightness until it fits, rather than
 *   being clipped, which would change the lightness too.
 * - The dimming multiplies `Y + 0.05` of every role of the light half by the same factor, the accents, the second
 *   accent and the white on them included, so every ratio between two of them stays as it is - the chords' 4.5:1 on the
 *   cards and the text's on the paper alike. It is what makes room for the paper: near white, sRGB holds almost no
 *   chroma, so a light background warmed at its own lightness stays all but white, while the same tint at the lightness
 *   of a page of an old book is a sepia. The accents keep their hue and come out a shade deeper, as they would on that
 *   page. The dark half needs none of it, and its accents are left as the palette has them.
 *
 * That is what lets this be a slider rather than a list of hand-checked palettes.
 */
internal fun ColorSchemePair.withBackgroundWarmth(level: Int): ColorSchemePair {
    val amount = level.coerceIn(0, UserPreferences.MAX_BACKGROUND_WARMTH) / UserPreferences.MAX_BACKGROUND_WARMTH.toDouble()
    if (amount == 0.0) return this
    val dimming = 1 - (1 - MIN_LIGHT_LUMINANCE_SCALE) * amount
    // Dimmed before it is warmed, so that the sepia is worked out at the lightness the color ends up at.
    return copy(
        light = light.mapColors { it.dimmed(dimming) }.warmed(amount),
        dark = dark.warmed(amount),
        lightSecondAccent = lightSecondAccent.dimmed(dimming),
    )
}

private fun ColorScheme.warmed(amount: Double) = copy(
    background = background.warmed(amount),
    onBackground = onBackground.warmed(amount),
    surface = surface.warmed(amount),
    onSurface = onSurface.warmed(amount),
    surfaceVariant = surfaceVariant.warmed(amount),
    onSurfaceVariant = onSurfaceVariant.warmed(amount),
    inverseSurface = inverseSurface.warmed(amount),
    inverseOnSurface = inverseOnSurface.warmed(amount),
    outline = outline.warmed(amount),
    outlineVariant = outlineVariant.warmed(amount),
    surfaceBright = surfaceBright.warmed(amount),
    surfaceDim = surfaceDim.warmed(amount),
    surfaceContainerLowest = surfaceContainerLowest.warmed(amount),
    surfaceContainerLow = surfaceContainerLow.warmed(amount),
    surfaceContainer = surfaceContainer.warmed(amount),
    surfaceContainerHigh = surfaceContainerHigh.warmed(amount),
    surfaceContainerHighest = surfaceContainerHighest.warmed(amount),
)

private fun ColorScheme.mapColors(transform: (Color) -> Color) = copy(
    primary = transform(primary),
    onPrimary = transform(onPrimary),
    primaryContainer = transform(primaryContainer),
    onPrimaryContainer = transform(onPrimaryContainer),
    inversePrimary = transform(inversePrimary),
    secondary = transform(secondary),
    onSecondary = transform(onSecondary),
    secondaryContainer = transform(secondaryContainer),
    onSecondaryContainer = transform(onSecondaryContainer),
    tertiary = transform(tertiary),
    onTertiary = transform(onTertiary),
    tertiaryContainer = transform(tertiaryContainer),
    onTertiaryContainer = transform(onTertiaryContainer),
    background = transform(background),
    onBackground = transform(onBackground),
    surface = transform(surface),
    onSurface = transform(onSurface),
    surfaceVariant = transform(surfaceVariant),
    onSurfaceVariant = transform(onSurfaceVariant),
    surfaceTint = transform(surfaceTint),
    inverseSurface = transform(inverseSurface),
    inverseOnSurface = transform(inverseOnSurface),
    error = transform(error),
    onError = transform(onError),
    errorContainer = transform(errorContainer),
    onErrorContainer = transform(onErrorContainer),
    outline = transform(outline),
    outlineVariant = transform(outlineVariant),
    scrim = transform(scrim),
    surfaceBright = transform(surfaceBright),
    surfaceDim = transform(surfaceDim),
    surfaceContainer = transform(surfaceContainer),
    surfaceContainerHigh = transform(surfaceContainerHigh),
    surfaceContainerHighest = transform(surfaceContainerHighest),
    surfaceContainerLow = transform(surfaceContainerLow),
    surfaceContainerLowest = transform(surfaceContainerLowest),
    primaryFixed = transform(primaryFixed),
    primaryFixedDim = transform(primaryFixedDim),
    onPrimaryFixed = transform(onPrimaryFixed),
    onPrimaryFixedVariant = transform(onPrimaryFixedVariant),
    secondaryFixed = transform(secondaryFixed),
    secondaryFixedDim = transform(secondaryFixedDim),
    onSecondaryFixed = transform(onSecondaryFixed),
    onSecondaryFixedVariant = transform(onSecondaryFixedVariant),
    tertiaryFixed = transform(tertiaryFixed),
    tertiaryFixedDim = transform(tertiaryFixedDim),
    onTertiaryFixed = transform(onTertiaryFixed),
    onTertiaryFixedVariant = transform(onTertiaryFixedVariant),
)

/**
 * The color moved [amount] of the way from its own tint to sepia at its own lightness. The sepia grows less saturated
 * the darker it is (with the square root of L*), so that the light half is a paper and the dark half a brown that is
 * still read as a night rather than as wood.
 */
internal fun Color.warmed(amount: Double): Color {
    val lab = toLab()
    val chroma = SEPIA_MAX_CHROMA * sqrt(lab.l.coerceIn(0.0, 100.0) / 100.0)
    return inGamut(
        l = lab.l,
        a = lab.a + (chroma * cos(SEPIA_HUE) - lab.a) * amount,
        b = lab.b + (chroma * sin(SEPIA_HUE) - lab.b) * amount,
        alpha = alpha,
    )
}

/**
 * The color with `Y + 0.05` of its relative luminance multiplied by [scale], at its own hue and chroma where they still
 * fit. A role so dark that the product would be below black is held at black, which only a near-black of under 1%
 * luminance at the deepest dimming is.
 */
private fun Color.dimmed(scale: Double): Color {
    val lab = toLab()
    val luminance = (scale * (labFInverse((lab.l + 16) / 116) + 0.05) - 0.05).coerceAtLeast(0.0)
    return inGamut(l = 116 * labF(luminance) - 16, a = lab.a, b = lab.b, alpha = alpha)
}

/** The color at [l], [a] and [b], with as much of its chroma as fits in sRGB at that lightness. */
private fun inGamut(l: Double, a: Double, b: Double, alpha: Float): Color {
    // Neutral is in gamut at every lightness, so the largest share of the chroma that still fits is always found.
    var scale = 1.0
    if (!isInGamut(l, a, b)) {
        var low = 0.0
        var high = 1.0
        repeat(GAMUT_SEARCH_STEPS) {
            val middle = (low + high) / 2
            if (isInGamut(l, a * middle, b * middle)) low = middle else high = middle
        }
        scale = low
    }
    return labToColor(l, a * scale, b * scale, alpha)
}

private class Lab(val l: Double, val a: Double, val b: Double)

private fun Color.toLab(): Lab {
    val r = red.toDouble().toLinear()
    val g = green.toDouble().toLinear()
    val b = blue.toDouble().toLinear()
    val fx = labF((0.4124564 * r + 0.3575761 * g + 0.1804375 * b) / WHITE_X)
    val fy = labF(0.2126729 * r + 0.7151522 * g + 0.0721750 * b)
    val fz = labF((0.0193339 * r + 0.1191920 * g + 0.9503041 * b) / WHITE_Z)
    return Lab(l = 116 * fy - 16, a = 500 * (fx - fy), b = 200 * (fy - fz))
}

private fun linearRgbOf(l: Double, a: Double, b: Double): Triple<Double, Double, Double> {
    val fy = (l + 16) / 116
    val x = WHITE_X * labFInverse(fy + a / 500)
    val y = labFInverse(fy)
    val z = WHITE_Z * labFInverse(fy - b / 200)
    return Triple(
        3.2404542 * x - 1.5371385 * y - 0.4985314 * z,
        -0.9692660 * x + 1.8760108 * y + 0.0415560 * z,
        0.0556434 * x - 0.2040259 * y + 1.0572252 * z,
    )
}

private fun isInGamut(l: Double, a: Double, b: Double) =
    linearRgbOf(l, a, b).toList().all { it in -GAMUT_TOLERANCE..1 + GAMUT_TOLERANCE }

private fun labToColor(l: Double, a: Double, b: Double, alpha: Float): Color {
    val (r, g, bl) = linearRgbOf(l, a, b)
    return Color(
        red = r.toGamma().toFloat(),
        green = g.toGamma().toFloat(),
        blue = bl.toGamma().toFloat(),
        alpha = alpha,
    )
}

private fun Double.toLinear() = if (this <= 0.04045) this / 12.92 else ((this + 0.055) / 1.055).pow(2.4)

private fun Double.toGamma() = coerceIn(0.0, 1.0).let { if (it <= 0.0031308) it * 12.92 else 1.055 * it.pow(1 / 2.4) - 0.055 }

private fun labF(t: Double) = if (t > LAB_EPSILON) cbrt(t) else t / (3 * LAB_DELTA * LAB_DELTA) + 4.0 / 29

private fun labFInverse(t: Double) = if (t > LAB_DELTA) t * t * t else 3 * LAB_DELTA * LAB_DELTA * (t - 4.0 / 29)

/**
 * The hue of the sepia, in radians: the yellow of aged paper. A redder one reads as peach on the containers, and sRGB
 * holds far less chroma near white at a redder hue, so the background itself would stay all but white.
 */
private const val SEPIA_HUE = 85 * PI / 180

/**
 * How far the light half is dimmed at full warmth, as the factor on `Y + 0.05`: it takes the light backgrounds from
 * about L* 98 to about 92, the lightness of an old book's page, where they can hold the sepia.
 */
internal const val MIN_LIGHT_LUMINANCE_SCALE = 0.86

/** The chroma of the sepia at full lightness, about that of a paper-colored reading mode at its most yellow. */
private const val SEPIA_MAX_CHROMA = 13.0

private const val WHITE_X = 0.95047
private const val WHITE_Z = 1.08883
private const val LAB_DELTA = 6.0 / 29
private const val LAB_EPSILON = LAB_DELTA * LAB_DELTA * LAB_DELTA
private const val GAMUT_TOLERANCE = 0.0001
private const val GAMUT_SEARCH_STEPS = 16
