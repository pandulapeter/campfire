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

import android.R
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import kotlin.math.pow
import kotlin.math.sqrt

@Composable
internal actual fun dynamicColorSchemePair(): ColorSchemePair? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    val context = LocalContext.current
    remember(context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE || context.hasSystemRoleColors()) {
            ColorSchemePair(
                light = dynamicLightColorScheme(context),
                dark = dynamicDarkColorScheme(context),
            )
        } else {
            SystemPalettes(context).colorSchemePair()
        }
    }
} else {
    null
}

/**
 * Whether the role colors Android 14 added (`system_primary_light` and the rest) follow the wallpaper on this device,
 * which is what Material's [dynamicLightColorScheme] reads from that version on instead of the tonal palette.
 *
 * Samsung's One UI recolors only the tonal palette (`system_accent1_600` and its siblings) and leaves the role colors
 * at the framework's defaults, which are blue, so read as they are they paint every Samsung phone blue whatever the
 * wallpaper made its palette. Where the role colors are recolored they are tones of that same palette, so a primary
 * that is nowhere near the palette's own tone 40 is one nobody overlaid. The distance allows for the variants that
 * pick a slightly different tone for the role, and is far below that between two hues.
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
private fun Context.hasSystemRoleColors(): Boolean {
    val role = getColor(R.color.system_primary_light)
    val palette = getColor(R.color.system_accent1_600)
    fun channelDistance(shift: Int) = ((role shr shift and 0xFF) - (palette shr shift and 0xFF)).toDouble()
    return sqrt(channelDistance(16).pow(2) + channelDistance(8).pow(2) + channelDistance(0).pow(2)) < 64.0
}

/**
 * The five tonal palettes the system derived from the wallpaper, and the color scheme Material maps them to, for the
 * devices [hasSystemRoleColors] finds without role colors of their own.
 *
 * The system hands out thirteen tones of each palette, and the surface roles ask for tones between them (a container
 * at 94, a dim surface at 87). Those are mixed from the two neighbors in linear light, in the proportion that lands
 * exactly on the tone's luminance: the neighbors are the same hue at nearly the same chroma, so the mix is the color
 * the palette would have had there, to within what can be seen.
 */
@RequiresApi(Build.VERSION_CODES.S)
private class SystemPalettes(context: Context) {
    private val primary = context.palette(PRIMARY)
    private val secondary = context.palette(SECONDARY)
    private val tertiary = context.palette(TERTIARY)
    private val neutral = context.palette(NEUTRAL)
    private val neutralVariant = context.palette(NEUTRAL_VARIANT)

    fun colorSchemePair() = ColorSchemePair(
        light = lightColorScheme(
            primary = primary(40),
            onPrimary = primary(100),
            primaryContainer = primary(90),
            onPrimaryContainer = primary(10),
            inversePrimary = primary(80),
            secondary = secondary(40),
            onSecondary = secondary(100),
            secondaryContainer = secondary(90),
            onSecondaryContainer = secondary(10),
            tertiary = tertiary(40),
            onTertiary = tertiary(100),
            tertiaryContainer = tertiary(90),
            onTertiaryContainer = tertiary(10),
            background = neutral(98),
            onBackground = neutral(10),
            surface = neutral(98),
            onSurface = neutral(10),
            surfaceVariant = neutralVariant(90),
            onSurfaceVariant = neutralVariant(30),
            surfaceTint = primary(40),
            inverseSurface = neutral(20),
            inverseOnSurface = neutral(95),
            outline = neutralVariant(50),
            outlineVariant = neutralVariant(80),
            scrim = neutral(0),
            surfaceBright = neutral(98),
            surfaceDim = neutral(87),
            surfaceContainerLowest = neutral(100),
            surfaceContainerLow = neutral(96),
            surfaceContainer = neutral(94),
            surfaceContainerHigh = neutral(92),
            surfaceContainerHighest = neutral(90),
        ),
        dark = darkColorScheme(
            primary = primary(80),
            onPrimary = primary(20),
            primaryContainer = primary(30),
            onPrimaryContainer = primary(90),
            inversePrimary = primary(40),
            secondary = secondary(80),
            onSecondary = secondary(20),
            secondaryContainer = secondary(30),
            onSecondaryContainer = secondary(90),
            tertiary = tertiary(80),
            onTertiary = tertiary(20),
            tertiaryContainer = tertiary(30),
            onTertiaryContainer = tertiary(90),
            background = neutral(6),
            onBackground = neutral(90),
            surface = neutral(6),
            onSurface = neutral(90),
            surfaceVariant = neutralVariant(30),
            onSurfaceVariant = neutralVariant(80),
            surfaceTint = primary(80),
            inverseSurface = neutral(90),
            inverseOnSurface = neutral(20),
            outline = neutralVariant(60),
            outlineVariant = neutralVariant(30),
            scrim = neutral(0),
            surfaceBright = neutral(24),
            surfaceDim = neutral(6),
            surfaceContainerLowest = neutral(4),
            surfaceContainerLow = neutral(10),
            surfaceContainer = neutral(12),
            surfaceContainerHigh = neutral(17),
            surfaceContainerHighest = neutral(22),
        ),
    )

    private fun Context.palette(resources: IntArray): (Int) -> Color {
        val colors = resources.map { getColor(it) }
        return { tone ->
            val above = TONES.indexOfLast { it >= tone }
            if (TONES[above] == tone) {
                Color(colors[above])
            } else {
                mix(dark = colors[above + 1], light = colors[above], luminance = luminanceOfTone(tone))
            }
        }
    }

    private fun mix(dark: Int, light: Int, luminance: Double): Color {
        val darkLuminance = luminance(dark)
        val fraction = (luminance - darkLuminance) / (luminance(light) - darkLuminance)
        fun channel(shift: Int): Float {
            val from = linear(dark shr shift and 0xFF)
            return gamma(from + (linear(light shr shift and 0xFF) - from) * fraction).toFloat()
        }
        return Color(red = channel(16), green = channel(8), blue = channel(0))
    }

    private fun luminance(color: Int) =
        0.2126 * linear(color shr 16 and 0xFF) + 0.7152 * linear(color shr 8 and 0xFF) + 0.0722 * linear(color and 0xFF)

    private fun linear(channel: Int) = (channel / 255.0).let {
        if (it <= 0.04045) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4)
    }

    private fun gamma(linear: Double) = linear.coerceIn(0.0, 1.0).let {
        if (it <= 0.0031308) it * 12.92 else 1.055 * it.pow(1 / 2.4) - 0.055
    }

    /** The relative luminance of CIE L* [tone], which is what a Material tone is. */
    private fun luminanceOfTone(tone: Int) = ((tone + 16) / 116.0).let {
        if (it > 6 / 29.0) it * it * it else 3 * (6 / 29.0).pow(2) * (it - 4 / 29.0)
    }

    private companion object {
        /** The tones the system hands out, from light to dark: `_0` is tone 100 and `_1000` tone 0. */
        val TONES = intArrayOf(100, 99, 95, 90, 80, 70, 60, 50, 40, 30, 20, 10, 0)
        val PRIMARY = intArrayOf(
            R.color.system_accent1_0, R.color.system_accent1_10, R.color.system_accent1_50, R.color.system_accent1_100,
            R.color.system_accent1_200, R.color.system_accent1_300, R.color.system_accent1_400,
            R.color.system_accent1_500, R.color.system_accent1_600, R.color.system_accent1_700,
            R.color.system_accent1_800, R.color.system_accent1_900, R.color.system_accent1_1000,
        )
        val SECONDARY = intArrayOf(
            R.color.system_accent2_0, R.color.system_accent2_10, R.color.system_accent2_50, R.color.system_accent2_100,
            R.color.system_accent2_200, R.color.system_accent2_300, R.color.system_accent2_400,
            R.color.system_accent2_500, R.color.system_accent2_600, R.color.system_accent2_700,
            R.color.system_accent2_800, R.color.system_accent2_900, R.color.system_accent2_1000,
        )
        val TERTIARY = intArrayOf(
            R.color.system_accent3_0, R.color.system_accent3_10, R.color.system_accent3_50, R.color.system_accent3_100,
            R.color.system_accent3_200, R.color.system_accent3_300, R.color.system_accent3_400,
            R.color.system_accent3_500, R.color.system_accent3_600, R.color.system_accent3_700,
            R.color.system_accent3_800, R.color.system_accent3_900, R.color.system_accent3_1000,
        )
        val NEUTRAL = intArrayOf(
            R.color.system_neutral1_0, R.color.system_neutral1_10, R.color.system_neutral1_50,
            R.color.system_neutral1_100, R.color.system_neutral1_200, R.color.system_neutral1_300,
            R.color.system_neutral1_400, R.color.system_neutral1_500, R.color.system_neutral1_600,
            R.color.system_neutral1_700, R.color.system_neutral1_800, R.color.system_neutral1_900,
            R.color.system_neutral1_1000,
        )
        val NEUTRAL_VARIANT = intArrayOf(
            R.color.system_neutral2_0, R.color.system_neutral2_10, R.color.system_neutral2_50,
            R.color.system_neutral2_100, R.color.system_neutral2_200, R.color.system_neutral2_300,
            R.color.system_neutral2_400, R.color.system_neutral2_500, R.color.system_neutral2_600,
            R.color.system_neutral2_700, R.color.system_neutral2_800, R.color.system_neutral2_900,
            R.color.system_neutral2_1000,
        )
    }
}
