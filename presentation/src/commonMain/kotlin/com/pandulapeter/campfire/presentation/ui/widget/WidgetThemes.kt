/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.widget

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.presentation.ui.theme.ColorSchemePair
import com.pandulapeter.campfire.presentation.ui.theme.colorSchemePair
import com.pandulapeter.campfire.presentation.ui.theme.dynamicColorSchemePair
import com.pandulapeter.campfire.presentation.ui.theme.withBackgroundWarmth

/**
 * The widgets' theme for [preferences]: the app's light or dark half where it is set to one, and the palette of its color,
 * warmed as far as the app is, unless that color is the system's own (Android's from the wallpaper), which the widgets
 * take from the system, where it follows a new wallpaper without the app.
 */
@Composable
internal fun rememberWidgetTheme(preferences: UserPreferences): WidgetSnapshot.Theme {
    val isSystemColors = preferences.themeColor == UserPreferences.ThemeColor.SYSTEM && dynamicColorSchemePair() != null
    val colors = colorSchemePair(preferences.themeColor)
    return remember(preferences.uiMode, isSystemColors, colors, preferences.backgroundWarmth) {
        widgetTheme(
            uiMode = preferences.uiMode,
            colors = colors.takeUnless { isSystemColors }?.withBackgroundWarmth(preferences.backgroundWarmth),
        )
    }
}

/** The widgets' theme in [uiMode]'s half, painted in [colors], or in the system's colors where there are none. */
internal fun widgetTheme(uiMode: UserPreferences.UiMode?, colors: ColorSchemePair?) = WidgetSnapshot.Theme(
    isDark = when (uiMode) {
        UserPreferences.UiMode.LIGHT -> false
        UserPreferences.UiMode.DARK -> true
        UserPreferences.UiMode.SYSTEM_DEFAULT, null -> null
    },
    light = colors?.light?.toWidgetPalette(),
    dark = colors?.dark?.toWidgetPalette(),
)

/** A widget is a container on the home screen, so it takes the scheme's container color, as the app's cards do. */
private fun ColorScheme.toWidgetPalette() = WidgetSnapshot.Palette(
    background = surfaceContainer.argb,
    accent = primary.argb,
    text = onSurface.argb,
    secondaryText = onSurfaceVariant.argb,
)

private val Color.argb get() = toArgb().toLong() and 0xFFFFFFFFL
