/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.awt.ComposeWindow
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.theme.isDarkTheme

/**
 * Draws the window buttons, and on macOS the rim along the window's top edge, for the theme the app is in rather than
 * the system's, since with the content laid out under them the app's background is theirs: dark mode buttons on a
 * light theme are all but invisible, and the other way around. The macOS property exists only in the JetBrains
 * Runtime, which the build packages for this; any other JDK ignores it and keeps the system's appearance.
 */
@Composable
internal fun TitleBarAppearance(
    window: ComposeWindow,
    titleBar: ExtendedTitleBar,
    viewModel: CampfireViewModel,
) {
    val isDarkTheme = viewModel.userPreferences.collectAsState().value?.uiMode.isDarkTheme()
    SideEffect {
        if (isMacOs) {
            window.rootPane.putClientProperty("apple.awt.windowAppearance", if (isDarkTheme) "NSAppearanceNameDarkAqua" else "NSAppearanceNameAqua")
        }
        // Every property set redraws the title bar, and this runs with every recomposition.
        if (isWindows && titleBar.customTitleBar.properties[WINDOWS_DARK_CONTROLS] != isDarkTheme) {
            titleBar.customTitleBar.putProperty(WINDOWS_DARK_CONTROLS, isDarkTheme)
        }
    }
}

/** Whether the caption buttons are drawn for a dark background (light icons) or a light one. */
private const val WINDOWS_DARK_CONTROLS = "controls.dark"
