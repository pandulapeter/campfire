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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.pandulapeter.campfire.data.model.domain.UserPreferences

/**
 * The two halves of one color scheme. They travel together because the light and the dark theme are not a choice
 * between two palettes but one palette seen two ways: whichever color the user picked has to answer both, and the
 * theme cross fades from one to the other.
 */
internal data class ColorSchemePair(
    val light: ColorScheme,
    val dark: ColorScheme,
)

/**
 * Every color the app can be painted in, in the order the settings screen offers them, which is the order the
 * preference itself declares - the app's own first, then the system's where there is one, then the rest around the
 * hue circle.
 *
 * [UserPreferences.ThemeColor.SYSTEM] is the only entry that is not a constant, since it is whatever the operating
 * system derived from the wallpaper, and the only one that can be absent: where nothing hands out such a palette it
 * drops out of the list entirely, which is both what stops the settings screen from offering it and what makes a
 * preference asking for it fall back, see [colorSchemePair].
 */
@Composable
internal fun themeColorOptions(): List<Pair<UserPreferences.ThemeColor, ColorSchemePair>> {
    val systemColorSchemePair = dynamicColorSchemePair()
    return remember(systemColorSchemePair) {
        UserPreferences.ThemeColor.entries.mapNotNull { themeColor ->
            when (themeColor) {
                UserPreferences.ThemeColor.CAMPFIRE -> CampfireColorScheme
                UserPreferences.ThemeColor.SYSTEM -> systemColorSchemePair
                UserPreferences.ThemeColor.RED -> MaterialColorSchemes.Red
                UserPreferences.ThemeColor.YELLOW -> MaterialColorSchemes.Yellow
                UserPreferences.ThemeColor.GREEN -> MaterialColorSchemes.Green
                UserPreferences.ThemeColor.TEAL -> MaterialColorSchemes.Teal
                UserPreferences.ThemeColor.BLUE -> MaterialColorSchemes.Blue
                UserPreferences.ThemeColor.PURPLE -> MaterialColorSchemes.Purple
                UserPreferences.ThemeColor.PINK -> MaterialColorSchemes.Pink
            }?.let { themeColor to it }
        }
    }
}

/**
 * The palette a stored preference stands for, falling back to the app's own orange for one this device cannot
 * honor - a preferences file restored onto a device whose system hands out no colors of its own, rather than a
 * choice being ignored, since the settings screen only ever offers what [themeColorOptions] has.
 */
@Composable
internal fun colorSchemePair(themeColor: UserPreferences.ThemeColor?) =
    themeColorOptions().firstOrNull { (value, _) -> value == themeColor }?.second ?: CampfireColorScheme
