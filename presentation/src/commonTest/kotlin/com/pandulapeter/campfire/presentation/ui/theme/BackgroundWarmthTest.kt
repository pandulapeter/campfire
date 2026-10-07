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
import androidx.compose.ui.graphics.luminance
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class BackgroundWarmthTest {

    private val palettes = listOf(
        CampfireColorScheme,
        OrangeColorScheme,
        GrayColorScheme,
        MaterialColorSchemes.Red,
        MaterialColorSchemes.Yellow,
        MaterialColorSchemes.Green,
        MaterialColorSchemes.Teal,
        MaterialColorSchemes.Blue,
        MaterialColorSchemes.Purple,
        MaterialColorSchemes.Pink,
    )

    @Test
    fun noWarmthIsThePaletteItself() = palettes.forEach { assertSame(it, it.withBackgroundWarmth(0)) }

    @Test
    fun theDarkHalfKeepsTheLuminanceOfEveryNeutral() = palettes.forEach { palette ->
        (1..UserPreferences.MAX_BACKGROUND_WARMTH).forEach { level ->
            palette.dark.neutrals().zip(palette.withBackgroundWarmth(level).dark.neutrals()).forEach { (before, after) ->
                // Eight bits a channel is all a color is stored in, which is all the difference allowed.
                assertTrue(abs(before.luminance() - after.luminance()) < 0.004f, "$before became $after at level $level")
            }
        }
    }

    @Test
    fun everyLevelKeepsEveryContrastOfTheScheme() = palettes.forEach { palette ->
        (1..UserPreferences.MAX_BACKGROUND_WARMTH).forEach { level ->
            val warmed = palette.withBackgroundWarmth(level)
            listOf(
                palette.light.withSecondAccent(palette.lightSecondAccent) to warmed.light.withSecondAccent(warmed.lightSecondAccent),
                palette.dark.withSecondAccent(palette.darkSecondAccent) to warmed.dark.withSecondAccent(warmed.darkSecondAccent),
            ).forEach { (original, changed) ->
                original.indices.forEach { first ->
                    original.indices.forEach { second ->
                        // Within two percent, which is what rounding a near-black to eight bits a channel can move it by.
                        val ratio = contrast(changed[first], changed[second]) / contrast(original[first], original[second])
                        assertEquals(1f, ratio, 0.02f, "${original[first]} on ${original[second]} at level $level")
                    }
                }
            }
        }
    }

    @Test
    fun theAccentsKeepTheirColor() {
        val warmed = CampfireColorScheme.withBackgroundWarmth(UserPreferences.MAX_BACKGROUND_WARMTH)
        assertEquals(CampfireColorScheme.dark.tertiary, warmed.dark.tertiary)
        assertEquals(CampfireColorScheme.darkSecondAccent, warmed.darkSecondAccent)
        // In the light half they come out a shade deeper, and stay the hue that was picked.
        val primary = warmed.light.primary
        assertTrue(primary.luminance() < CampfireColorScheme.light.primary.luminance())
        assertTrue(primary.blue > primary.red && primary.red > primary.green, "$primary is no longer the purple")
    }

    @Test
    fun fullWarmthIsSepia() {
        val background = CampfireColorScheme.withBackgroundWarmth(UserPreferences.MAX_BACKGROUND_WARMTH).light.background
        assertTrue(background.red >= background.green && background.green > background.blue, "$background is not a warm paper")
        assertTrue(background.red - background.blue > 0.05f, "$background is all but white")
        val gray = Color(0xFF808080).warmed(1.0)
        assertTrue(gray.red > gray.blue, "$gray is not warm")
    }

    private fun ColorScheme.neutrals() = listOf(
        background, onBackground, surface, onSurface, surfaceVariant, onSurfaceVariant, inverseSurface, inverseOnSurface,
        outline, outlineVariant, surfaceBright, surfaceDim, surfaceContainerLowest, surfaceContainerLow, surfaceContainer,
        surfaceContainerHigh, surfaceContainerHighest,
    )

    /** The roles a foreground or a background is drawn in, with the second accent, which the scheme has no role for. */
    private fun ColorScheme.withSecondAccent(secondAccent: Color) = listOf(
        primary, onPrimary, primaryContainer, onPrimaryContainer, secondary, onSecondary, secondaryContainer,
        onSecondaryContainer, tertiary, onTertiary, tertiaryContainer, onTertiaryContainer, error, onError, errorContainer,
        onErrorContainer, secondAccent,
    ) + neutrals()

    private fun contrast(first: Color, second: Color): Float {
        val lighter = maxOf(first.luminance(), second.luminance())
        val darker = minOf(first.luminance(), second.luminance())
        return (lighter + 0.05f) / (darker + 0.05f)
    }
}
