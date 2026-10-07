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
    fun everyLevelKeepsTheLuminanceOfEveryNeutral() = palettes.forEach { palette ->
        (1..UserPreferences.MAX_BACKGROUND_WARMTH).forEach { level ->
            val warmed = palette.withBackgroundWarmth(level)
            listOf(palette.light to warmed.light, palette.dark to warmed.dark).forEach { (original, changed) ->
                original.neutrals().zip(changed.neutrals()).forEach { (before, after) ->
                    // Eight bits a channel is all a color is stored in, which is all the difference allowed.
                    assertTrue(abs(before.luminance() - after.luminance()) < 0.004f, "$before became $after at level $level")
                }
            }
        }
    }

    @Test
    fun everyLevelKeepsTheContrastOfTextOnTheBackground() = palettes.forEach { palette ->
        val warmed = palette.withBackgroundWarmth(UserPreferences.MAX_BACKGROUND_WARMTH)
        listOf(palette.light to warmed.light, palette.dark to warmed.dark).forEach { (original, changed) ->
            // Within a percent, which is what rounding a near-black text color to eight bits a channel moves it by.
            assertEquals(1f, contrast(changed.onSurface, changed.surface) / contrast(original.onSurface, original.surface), 0.01f)
            assertEquals(1f, contrast(changed.onSurfaceVariant, changed.surfaceContainerHigh) / contrast(original.onSurfaceVariant, original.surfaceContainerHigh), 0.01f)
        }
    }

    @Test
    fun theAccentsAreLeftAlone() {
        val warmed = CampfireColorScheme.withBackgroundWarmth(UserPreferences.MAX_BACKGROUND_WARMTH)
        assertEquals(CampfireColorScheme.light.primary, warmed.light.primary)
        assertEquals(CampfireColorScheme.dark.tertiary, warmed.dark.tertiary)
        assertEquals(CampfireColorScheme.lightSecondAccent, warmed.lightSecondAccent)
        assertEquals(CampfireColorScheme.darkSecondAccent, warmed.darkSecondAccent)
    }

    @Test
    fun fullWarmthIsSepia() {
        val background = CampfireColorScheme.withBackgroundWarmth(UserPreferences.MAX_BACKGROUND_WARMTH).light.background
        assertTrue(background.red >= background.green && background.green > background.blue, "$background is not a warm paper")
        val gray = Color(0xFF808080).warmed(1.0)
        assertTrue(gray.red > gray.blue, "$gray is not warm")
    }

    private fun ColorScheme.neutrals() = listOf(
        background, onBackground, surface, onSurface, surfaceVariant, onSurfaceVariant, inverseSurface, inverseOnSurface,
        outline, outlineVariant, surfaceBright, surfaceDim, surfaceContainerLowest, surfaceContainerLow, surfaceContainer,
        surfaceContainerHigh, surfaceContainerHighest,
    )

    private fun contrast(first: Color, second: Color): Float {
        val lighter = maxOf(first.luminance(), second.luminance())
        val darker = minOf(first.luminance(), second.luminance())
        return (lighter + 0.05f) / (darker + 0.05f)
    }
}
