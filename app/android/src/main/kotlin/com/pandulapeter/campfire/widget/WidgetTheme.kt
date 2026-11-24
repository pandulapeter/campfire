/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.widget

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.material3.ColorProviders
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.ui.widget.widgetDarkColorScheme
import com.pandulapeter.campfire.presentation.ui.widget.widgetLightColorScheme

/**
 * The widgets' colors: the wallpaper's from Android 12 on, which Glance's own theme hands out, as the rest of the home
 * screen is, and the app's purple and orange before it. The text is in the system's typeface, since a widget's text is
 * drawn by the launcher, which cannot load the app's bundled Inter.
 */
@Composable
internal fun WidgetTheme(content: @Composable () -> Unit) = GlanceTheme(
    colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) GlanceTheme.colors else campfireColors,
    content = content,
)

private val campfireColors by lazy { ColorProviders(light = widgetLightColorScheme, dark = widgetDarkColorScheme) }

/** The widget's whole area, in the system's widget shape and background. */
@Composable
internal fun GlanceModifier.widgetBackground() = fillMaxSize()
    .appWidgetBackground()
    .background(GlanceTheme.colors.widgetBackground)
    .let { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) it.cornerRadius(android.R.dimen.system_app_widget_background_radius) else it }
    .padding(WIDGET_PADDING.dp)

/** The space around a widget's content, in dp. */
internal const val WIDGET_PADDING = 12
