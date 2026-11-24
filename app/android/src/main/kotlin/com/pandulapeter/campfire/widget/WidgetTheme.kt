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

import android.content.Context
import android.os.Build
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.color.ColorProviders
import androidx.glance.color.colorProviders
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.unit.ColorProvider
import com.pandulapeter.campfire.presentation.ui.widget.WidgetSnapshot
import com.pandulapeter.campfire.presentation.ui.widget.widgetDarkColorScheme
import com.pandulapeter.campfire.presentation.ui.widget.widgetLightColorScheme
import androidx.glance.color.ColorProvider as DayNightColorProvider
import androidx.glance.material3.ColorProviders as MaterialColorProviders

/**
 * The widgets' colors, the app's theme ([WidgetSnapshot.Theme]): its palette where it has one of its own, else the
 * wallpaper's, which Glance's own theme hands out from Android 12 on; in the app's light or dark half where it is set to
 * one, else the system's. A fixed half is also the one a launcher below Android 12 keeps right, where a day and night pair
 * is resolved once per drawing and stays in the half it was drawn in. With no theme (a snapshot from before one was
 * written, or none at all), the system's colors from Android 12 on and the app's own purple and orange before it.
 *
 * The text is in the system's typeface, since a widget's text is drawn by the launcher, which cannot load the app's
 * bundled Inter.
 */
@Composable
internal fun WidgetTheme(
    theme: WidgetSnapshot.Theme?,
    content: @Composable () -> Unit,
) = GlanceTheme(
    colors = widgetColors(theme = theme, context = LocalContext.current) ?: GlanceTheme.colors,
    content = content,
)

/** The colors [theme] names, or null for Glance's own, the wallpaper's in the system's half. */
private fun widgetColors(theme: WidgetSnapshot.Theme?, context: Context): ColorProviders? {
    val light = theme?.light
    val dark = theme?.dark
    val isDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    return when {
        light != null && dark != null -> when (theme.isDark) {
            null -> paletteColors(day = light, night = dark)
            true -> paletteColors(day = dark)
            false -> paletteColors(day = light)
        }
        isDynamic -> when (theme?.isDark) {
            null -> null
            true -> MaterialColorProviders(dynamicDarkColorScheme(context))
            false -> MaterialColorProviders(dynamicLightColorScheme(context))
        }
        else -> when (theme?.isDark) {
            null -> campfireColors
            true -> MaterialColorProviders(widgetDarkColorScheme)
            false -> MaterialColorProviders(widgetLightColorScheme)
        }
    }
}

private val campfireColors by lazy { MaterialColorProviders(light = widgetLightColorScheme, dark = widgetDarkColorScheme) }

/**
 * Every role Glance has, from the palette's four: the widgets draw only their background, accent, and two text colors, and
 * the rest are given the nearest of those so that nothing reads as an unrelated color. [night] is the dark half where the
 * system picks it, none where the half is fixed.
 */
private fun paletteColors(day: WidgetSnapshot.Palette, night: WidgetSnapshot.Palette? = null): ColorProviders {
    fun provider(role: (WidgetSnapshot.Palette) -> Long) =
        if (night == null) ColorProvider(Color(role(day))) else DayNightColorProvider(day = Color(role(day)), night = Color(role(night)))
    val background = provider { it.background }
    val accent = provider { it.accent }
    val text = provider { it.text }
    val secondaryText = provider { it.secondaryText }
    return colorProviders(
        primary = accent,
        onPrimary = background,
        primaryContainer = background,
        onPrimaryContainer = text,
        secondary = accent,
        onSecondary = background,
        secondaryContainer = background,
        onSecondaryContainer = text,
        tertiary = accent,
        onTertiary = background,
        tertiaryContainer = background,
        onTertiaryContainer = text,
        error = accent,
        errorContainer = background,
        onError = background,
        onErrorContainer = text,
        background = background,
        onBackground = text,
        surface = background,
        onSurface = text,
        surfaceVariant = background,
        onSurfaceVariant = secondaryText,
        outline = secondaryText,
        inverseOnSurface = background,
        inverseSurface = text,
        inversePrimary = background,
        widgetBackground = background,
    )
}

/** The widget's whole area, in the system's widget shape and background. */
@Composable
internal fun GlanceModifier.widgetBackground() = fillMaxSize()
    .appWidgetBackground()
    .background(GlanceTheme.colors.widgetBackground)
    .let { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) it.cornerRadius(android.R.dimen.system_app_widget_background_radius) else it }
    .padding(WIDGET_PADDING.dp)

/** The space around a widget's content, in dp. */
internal const val WIDGET_PADDING = 12
