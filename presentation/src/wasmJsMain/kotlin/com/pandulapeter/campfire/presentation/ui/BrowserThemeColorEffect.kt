/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
@file:OptIn(ExperimentalWasmJsInterop::class)

package com.pandulapeter.campfire.presentation.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.presentation.ui.theme.colorSchemePair
import com.pandulapeter.campfire.presentation.ui.theme.isDarkTheme
import com.pandulapeter.campfire.presentation.ui.theme.withBackgroundWarmth
import kotlin.js.ExperimentalWasmJsInterop

/**
 * Keeps the `theme-color` of the page - what Chrome on Android and the browsers built on it paint their toolbar and the
 * status bar in - on the palette the app is drawn in. index.html can only name the app's own palette, in the system's
 * light or dark half, since the preferences are in OPFS and nothing reads them before the app does.
 *
 * A light scheme hands over its `surfaceContainerHigh`, the tone of the search pill, rather than its background:
 * Chrome ignores a color whose HSL lightness is above 0.94 and paints its own default toolbar instead, and every light
 * background is well above that, while every palette's `surfaceContainerHigh` is below it. A dark scheme hands over its
 * background, which Chrome takes as it is - but only while the system is in its light theme. With the system in
 * dark, Chrome keeps its own dark toolbar whatever the page asks for, so an app in the system's own theme only gets
 * a toolbar of its colors in the light half.
 *
 * It follows the preferences rather than the colors on screen, the way the Android shell's system bars do: the theme
 * cross fades between two schemes on every frame of a change, and the browsers animate a new `theme-color` of their
 * own accord. Both of the page's tags are written, so the one whose media query matches carries it whichever it is.
 */
@Composable
internal fun BrowserThemeColorEffect(viewModel: CampfireViewModel) {
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val isDarkTheme = userPreferences?.uiMode.isDarkTheme()
    val baseColorSchemePair = colorSchemePair(userPreferences?.themeColor)
    val backgroundWarmth = userPreferences?.backgroundWarmth ?: 0
    val colorSchemePair = remember(baseColorSchemePair, backgroundWarmth) { baseColorSchemePair.withBackgroundWarmth(backgroundWarmth) }
    val toolbarColor = if (isDarkTheme) colorSchemePair.dark.background else colorSchemePair.light.surfaceContainerHigh
    // Nothing is known until the preferences are, and index.html's own tags are the better guess until then.
    if (userPreferences != null) {
        LaunchedEffect(toolbarColor) {
            setBrowserThemeColor("#" + (toolbarColor.toArgb() and 0xFFFFFF).toString(16).padStart(6, '0'))
        }
    }
}

/** Writes [color], a `#rrggbb` string, into every `theme-color` tag of the page. */
private fun setBrowserThemeColor(color: String) {
    js(
        """document.querySelectorAll('meta[name="theme-color"]').forEach(function (meta) {
            meta.setAttribute('content', color);
        })"""
    )
}
