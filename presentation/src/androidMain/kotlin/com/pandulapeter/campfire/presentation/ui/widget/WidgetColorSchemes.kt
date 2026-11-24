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
import com.pandulapeter.campfire.presentation.ui.theme.CampfireColorScheme

/**
 * The app's own purple and orange palette, which `:app:android`'s Glance widgets are drawn in where the system hands out
 * no colors of its own (below Android 12); from Android 12 on they take the wallpaper's, as the rest of the home screen
 * does.
 */
val widgetLightColorScheme: ColorScheme get() = CampfireColorScheme.light

/** The dark half of [widgetLightColorScheme]'s palette. */
val widgetDarkColorScheme: ColorScheme get() = CampfireColorScheme.dark
