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

import androidx.compose.ui.graphics.Color

/**
 * The app's own palette, and the one every build starts out with: the two colors of the app icon's gradient (#5A49CA
 * and #F57C00) on the neutral surfaces of [GrayColorScheme]. The purple is what the interface is drawn in - the
 * controls, the links, the selection - and the orange is what is played, [ColorSchemePair.lightPlayed] and
 * [ColorSchemePair.darkPlayed]: the chords, the key and the capo, so that the part of a page read off it mid-song is the
 * warm one. The orange is also the tertiary color, for whatever in Material reaches for that.
 *
 * Every role taken from the purple or the orange has the lightness of the gray role it replaces (in Oklab, as the gray
 * was made), so every pair of roles keeps the contrast the gray gives it, and only the hue and some chroma are added -
 * less of it on the selection than on the accents, since a selected chip is a surface and not an accent. The light
 * scheme's primary is the icon's purple itself, which has the lightness of the gray primary to begin with. The orange
 * the chords are drawn in is darker than the icon's in the light scheme, where #F57C00 on the cards would be under
 * 3:1, and lighter in the dark one, and both are at least 4.5:1 against every surface a chord is drawn on - the
 * darkest of them in the light scheme being the chorus card's `surfaceContainerHigh`.
 */
internal val CampfireColorScheme = ColorSchemePair(
    light = GrayColorScheme.light.copy(
        primary = Color(0xFF5A49CA),
        primaryContainer = Color(0xFFBBBDFD),
        onPrimaryContainer = Color(0xFF302B66),
        inversePrimary = Color(0xFFC1C3FF),
        secondaryContainer = Color(0xFFCBCDE6),
        onSecondaryContainer = Color(0xFF454664),
        tertiary = Color(0xFFA45204),
        tertiaryContainer = Color(0xFFFED9C2),
        onTertiaryContainer = Color(0xFF633B1F),
        surfaceTint = Color(0xFF5A49CA),
    ),
    dark = GrayColorScheme.dark.copy(
        primary = Color(0xFFC1C3FF),
        onPrimary = Color(0xFF2C2761),
        primaryContainer = Color(0xFF9697DC),
        onPrimaryContainer = Color(0xFF1A1541),
        inversePrimary = Color(0xFF5A49CA),
        secondaryContainer = Color(0xFF47485D),
        onSecondaryContainer = Color(0xFFD2D3E9),
        tertiary = Color(0xFFFE851E),
        onTertiary = Color(0xFF4B2507),
        tertiaryContainer = Color(0xFF5F3D26),
        onTertiaryContainer = Color(0xFFFED9C2),
        surfaceTint = Color(0xFFC1C3FF),
    ),
    lightPlayed = Color(0xFFA45204),
    darkPlayed = Color(0xFFFE851E),
)
