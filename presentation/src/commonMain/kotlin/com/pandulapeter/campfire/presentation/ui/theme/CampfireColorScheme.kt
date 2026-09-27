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

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * The app's own palette, and the one every build starts out with: dusk around a fire, in the two colors of the app
 * icon's gradient (#5A49CA and #F57C00). The purple is the primary color, what the interface is drawn in, and it tints
 * every neutral as well - a lavender paper in the light scheme and a violet night sky in the dark one - while the
 * orange is the fire: the tertiary color, and the second accent ([ColorSchemePair.lightSecondAccent] and
 * [ColorSchemePair.darkSecondAccent]), so that the chords, the key and the capo are the warm part of a page, and the
 * sticky headers the lists are filed under stand apart from every other title, which is purple.
 *
 * Every role is at the tone Material's own scheme puts it at (CIELAB lightness: the primary at 40 in the light scheme
 * and 80 in the dark one, the background at 98 and 6, the containers stepping by the same amounts), so the palette
 * keeps every contrast a Material component is designed around; #5A49CA happens to be exactly tone 40. The tones are
 * worked out in Oklch with the icon's hue turned a little towards magenta, since the purple's own hue reads as a
 * periwinkle blue once it is light, and with more chroma in the dark neutrals than in the light ones, which is what
 * makes the night read as violet rather than as a gray with a cast. The orange of the chords is darker than the icon's
 * in the light scheme, where #F57C00 on the cards would be under 3:1, and more vivid than the tertiary tone in the dark
 * one, and both are at least 4.5:1 against every surface a chord is drawn on. The one place the two meet is the palette's disc in the color choice, which is
 * the icon's gradient.
 */
internal val CampfireColorScheme = ColorSchemePair(
    light = lightColorScheme(
        primary = Color(0xFF5A49CA),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFE7DEFE),
        onPrimaryContainer = Color(0xFF573190),
        inversePrimary = Color(0xFFD0BCFF),
        secondary = Color(0xFF635A79),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFE6DEFB),
        onSecondaryContainer = Color(0xFF4B4260),
        tertiary = Color(0xFFA04F03),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFEDCC6),
        onTertiaryContainer = Color(0xFF6D3911),
        background = Color(0xFFFAF8FE),
        onBackground = Color(0xFF1D1A23),
        surface = Color(0xFFFAF8FE),
        onSurface = Color(0xFF1D1A23),
        surfaceVariant = Color(0xFFE5DFF5),
        onSurfaceVariant = Color(0xFF494456),
        surfaceTint = Color(0xFF5A49CA),
        inverseSurface = Color(0xFF322F39),
        inverseOnSurface = Color(0xFFF2EFF8),
        outline = Color(0xFF7A7488),
        outlineVariant = Color(0xFFC9C3D9),
        surfaceBright = Color(0xFFFAF8FE),
        surfaceDim = Color(0xFFDBD9E1),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF5F2FB),
        surfaceContainer = Color(0xFFEFEDF5),
        surfaceContainerHigh = Color(0xFFE9E7F0),
        surfaceContainerHighest = Color(0xFFE3E1EA),
    ),
    dark = darkColorScheme(
        primary = Color(0xFFD0BCFF),
        onPrimary = Color(0xFF3D2068),
        primaryContainer = Color(0xFF573190),
        onPrimaryContainer = Color(0xFFE7DEFE),
        inversePrimary = Color(0xFF5A49CA),
        secondary = Color(0xFFCBC1E5),
        onSecondary = Color(0xFF342C48),
        secondaryContainer = Color(0xFF4B4260),
        onSecondaryContainer = Color(0xFFE6DEFB),
        tertiary = Color(0xFFFDB78B),
        onTertiary = Color(0xFF4D2609),
        tertiaryContainer = Color(0xFF6D3911),
        onTertiaryContainer = Color(0xFFFEDCC6),
        background = Color(0xFF15121C),
        onBackground = Color(0xFFE3E1EA),
        surface = Color(0xFF15121C),
        onSurface = Color(0xFFE3E1EA),
        surfaceVariant = Color(0xFF494456),
        onSurfaceVariant = Color(0xFFC9C3D9),
        surfaceTint = Color(0xFFD0BCFF),
        inverseSurface = Color(0xFFE3E1EA),
        inverseOnSurface = Color(0xFF322F3B),
        outline = Color(0xFF938EA2),
        outlineVariant = Color(0xFF494456),
        surfaceBright = Color(0xFF3B3744),
        surfaceDim = Color(0xFF15121C),
        surfaceContainerLowest = Color(0xFF100C17),
        surfaceContainerLow = Color(0xFF1D1A25),
        surfaceContainer = Color(0xFF211E29),
        surfaceContainerHigh = Color(0xFF2C2834),
        surfaceContainerHighest = Color(0xFF36333F),
    ),
    lightSecondAccent = Color(0xFFA04F03),
    darkSecondAccent = Color(0xFFFE851E),
)
