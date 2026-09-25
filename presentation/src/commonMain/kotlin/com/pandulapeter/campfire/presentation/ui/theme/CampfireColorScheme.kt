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
 * The app's own palette, and the one every build starts out with: [OrangeColorScheme] with every color taken to the
 * gray of the same lightness (in Oklab, the way `app/generate_theme_icons.py` makes the gray icons), so that each role
 * keeps the contrast it has against every other, and only the error roles keep their red. It is gray because the icons
 * the system shows while the app is not running - the installed app, the Start menu, a store's page - are the ones no
 * theme color can reach, and gray is the one color that goes with whichever the user picks.
 *
 * The one role moved further is `secondaryContainer`, what Material marks a selected thing with (a tag in the song
 * list, a filter chip, the tab the navigation is on). In the orange it is as light as the surfaces it is drawn on and
 * told apart from them by its hue alone, so the gray of the same lightness is the surfaces' own; here it is a step
 * darker in the light scheme and lighter in the dark one, a little more than the hue set it apart by, since a
 * difference of lightness alone reads as less.
 */
internal val CampfireColorScheme = ColorSchemePair(
    light = lightColorScheme(
        primary = Color(0xFF606060),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFC4C4C4),
        onPrimaryContainer = Color(0xFF353535),
        inversePrimary = Color(0xFFC9C9C9),
        secondary = Color(0xFF5F5F5F),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFCFCFCF),
        onSecondaryContainer = Color(0xFF494949),
        tertiary = Color(0xFF5D5D5D),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFE1E1E1),
        onTertiaryContainer = Color(0xFF464646),
        background = Color(0xFFFAFAFA),
        onBackground = Color(0xFF1C1C1C),
        surface = Color(0xFFFAFAFA),
        onSurface = Color(0xFF1C1C1C),
        surfaceVariant = Color(0xFFE3E3E3),
        onSurfaceVariant = Color(0xFF474747),
        surfaceTint = Color(0xFF606060),
        inverseSurface = Color(0xFF303030),
        inverseOnSurface = Color(0xFFF1F1F1),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF93000A),
        outline = Color(0xFF787878),
        outlineVariant = Color(0xFFC7C7C7),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFFFAFAFA),
        surfaceDim = Color(0xFFDBDBDB),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF4F4F4),
        surfaceContainer = Color(0xFFEEEEEE),
        surfaceContainerHigh = Color(0xFFE8E8E8),
        surfaceContainerHighest = Color(0xFFE3E3E3),
    ),
    dark = darkColorScheme(
        primary = Color(0xFFC9C9C9),
        onPrimary = Color(0xFF313131),
        primaryContainer = Color(0xFF9F9F9F),
        onPrimaryContainer = Color(0xFF1D1D1D),
        inversePrimary = Color(0xFF606060),
        secondary = Color(0xFFC7C7C7),
        onSecondary = Color(0xFF313131),
        secondaryContainer = Color(0xFF4A4A4A),
        onSecondaryContainer = Color(0xFFD5D5D5),
        tertiary = Color(0xFFC5C5C5),
        onTertiary = Color(0xFF303030),
        tertiaryContainer = Color(0xFF464646),
        onTertiaryContainer = Color(0xFFE1E1E1),
        background = Color(0xFF141414),
        onBackground = Color(0xFFE3E3E3),
        surface = Color(0xFF141414),
        onSurface = Color(0xFFE3E3E3),
        surfaceVariant = Color(0xFF474747),
        onSurfaceVariant = Color(0xFFC7C7C7),
        surfaceTint = Color(0xFFC9C9C9),
        inverseSurface = Color(0xFFE3E3E3),
        inverseOnSurface = Color(0xFF313131),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        outline = Color(0xFF919191),
        outlineVariant = Color(0xFF474747),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFF393939),
        surfaceDim = Color(0xFF141414),
        surfaceContainerLowest = Color(0xFF0E0E0E),
        surfaceContainerLow = Color(0xFF1C1C1C),
        surfaceContainer = Color(0xFF202020),
        surfaceContainerHigh = Color(0xFF2A2A2A),
        surfaceContainerHighest = Color(0xFF353535),
    ),
)
