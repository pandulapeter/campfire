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

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.lerp
import com.pandulapeter.campfire.data.model.domain.UserPreferences

/**
 * Material 3 Expressive theme of the app. The two preferences behind it are independent - [themeColor] picks the
 * palette and [uiMode] picks which of its two halves is shown - and a change to either is animated the same way.
 *
 * The schemes are cross faded with a single progress value rather than by animating the color roles one by one:
 * a spring reaches its visibility threshold sooner the shorter the distance it has to cover, so the roles that
 * barely differ between the two schemes would snap over immediately while the rest were still on their way, and the
 * screen would appear to change in pieces.
 *
 * The very first change is the app correcting the guess it opened on - the system's setting, until the stored
 * preferences have been read - and it is cross faded like any other, because on a launch screen that is nothing but
 * a mark on the background, every color in the window flipping at once between two frames is the whole picture
 * blinking. It costs nothing when there is nothing to correct: a preference that resolves to the palette already on
 * screen is not a change and does not animate.
 *
 * @param content Told whether the scheme it is drawn in is the final one, which is what holds the launch screen in
 *   front of the app until the colors underneath have stopped moving.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CampfireTheme(
    uiMode: UserPreferences.UiMode?,
    themeColor: UserPreferences.ThemeColor?,
    content: @Composable (isThemeSettled: Boolean) -> Unit,
) {
    val isDarkTheme = uiMode.isDarkTheme()
    val colorSchemePair = colorSchemePair(themeColor)
    val targetColorScheme = if (isDarkTheme) colorSchemePair.dark else colorSchemePair.light
    val progress = remember { Animatable(1f) }
    var start by remember { mutableStateOf(targetColorScheme) }
    var stop by remember { mutableStateOf(targetColorScheme) }
    // The preferences rather than the scheme itself, which has no equality of its own to key an animation on.
    LaunchedEffect(isDarkTheme to themeColor) {
        // Two preferences can ask for the same palette - an unread one and the app's own color, a color the device
        // cannot honor and the orange it falls back to - and arriving at the scheme that is already on screen is
        // not a change to animate. The schemes are the constants of ColorSchemes.kt, so this is identity.
        if (targetColorScheme === stop) return@LaunchedEffect
        // The fade starts from the scheme being shown and not from the one the last change aimed at, so a second
        // change made while the first is still running continues from what the eye can see instead of jumping back
        // to where that one began - a switch away from the system palette right after switching to it, or a tap on
        // the dark theme while the color is still arriving.
        start = lerp(start, stop, progress.value)
        stop = targetColorScheme
        progress.snapTo(0f)
        progress.animateTo(1f, MOTION_SCHEME.defaultEffectsSpec())
    }
    MaterialExpressiveTheme(
        colorScheme = lerp(start, stop, progress.value),
        motionScheme = MOTION_SCHEME,
    ) {
        // The scheme asked for is not the one being shown from the composition the preferences arrive in until the
        // fade that follows has ended, and the effect above starts that fade one frame after that composition - so
        // the target being reached is read from the schemes rather than from the animation alone, which is not
        // running yet in that one frame.
        content(targetColorScheme === stop && !progress.isRunning)
    }
}

/**
 * Resolves whether the given user preference results in a dark theme, falling back to the system setting.
 */
@Composable
fun UserPreferences.UiMode?.isDarkTheme() = when (this) {
    UserPreferences.UiMode.LIGHT -> false
    UserPreferences.UiMode.DARK -> true
    UserPreferences.UiMode.SYSTEM_DEFAULT, null -> isSystemInDarkTheme()
}

/**
 * At rest the scheme is handed over as it is rather than as a fresh interpolation of itself, so that everything
 * reading a color role is not invalidated on every recomposition.
 */
private fun lerp(start: ColorScheme, stop: ColorScheme, fraction: Float) = when (fraction) {
    0f -> start
    1f -> stop
    else -> start.copy(
        primary = lerp(start.primary, stop.primary, fraction),
        onPrimary = lerp(start.onPrimary, stop.onPrimary, fraction),
        primaryContainer = lerp(start.primaryContainer, stop.primaryContainer, fraction),
        onPrimaryContainer = lerp(start.onPrimaryContainer, stop.onPrimaryContainer, fraction),
        inversePrimary = lerp(start.inversePrimary, stop.inversePrimary, fraction),
        secondary = lerp(start.secondary, stop.secondary, fraction),
        onSecondary = lerp(start.onSecondary, stop.onSecondary, fraction),
        secondaryContainer = lerp(start.secondaryContainer, stop.secondaryContainer, fraction),
        onSecondaryContainer = lerp(start.onSecondaryContainer, stop.onSecondaryContainer, fraction),
        tertiary = lerp(start.tertiary, stop.tertiary, fraction),
        onTertiary = lerp(start.onTertiary, stop.onTertiary, fraction),
        tertiaryContainer = lerp(start.tertiaryContainer, stop.tertiaryContainer, fraction),
        onTertiaryContainer = lerp(start.onTertiaryContainer, stop.onTertiaryContainer, fraction),
        background = lerp(start.background, stop.background, fraction),
        onBackground = lerp(start.onBackground, stop.onBackground, fraction),
        surface = lerp(start.surface, stop.surface, fraction),
        onSurface = lerp(start.onSurface, stop.onSurface, fraction),
        surfaceVariant = lerp(start.surfaceVariant, stop.surfaceVariant, fraction),
        onSurfaceVariant = lerp(start.onSurfaceVariant, stop.onSurfaceVariant, fraction),
        surfaceTint = lerp(start.surfaceTint, stop.surfaceTint, fraction),
        inverseSurface = lerp(start.inverseSurface, stop.inverseSurface, fraction),
        inverseOnSurface = lerp(start.inverseOnSurface, stop.inverseOnSurface, fraction),
        error = lerp(start.error, stop.error, fraction),
        onError = lerp(start.onError, stop.onError, fraction),
        errorContainer = lerp(start.errorContainer, stop.errorContainer, fraction),
        onErrorContainer = lerp(start.onErrorContainer, stop.onErrorContainer, fraction),
        outline = lerp(start.outline, stop.outline, fraction),
        outlineVariant = lerp(start.outlineVariant, stop.outlineVariant, fraction),
        scrim = lerp(start.scrim, stop.scrim, fraction),
        surfaceBright = lerp(start.surfaceBright, stop.surfaceBright, fraction),
        surfaceDim = lerp(start.surfaceDim, stop.surfaceDim, fraction),
        surfaceContainer = lerp(start.surfaceContainer, stop.surfaceContainer, fraction),
        surfaceContainerHigh = lerp(start.surfaceContainerHigh, stop.surfaceContainerHigh, fraction),
        surfaceContainerHighest = lerp(start.surfaceContainerHighest, stop.surfaceContainerHighest, fraction),
        surfaceContainerLow = lerp(start.surfaceContainerLow, stop.surfaceContainerLow, fraction),
        surfaceContainerLowest = lerp(start.surfaceContainerLowest, stop.surfaceContainerLowest, fraction),
        primaryFixed = lerp(start.primaryFixed, stop.primaryFixed, fraction),
        primaryFixedDim = lerp(start.primaryFixedDim, stop.primaryFixedDim, fraction),
        onPrimaryFixed = lerp(start.onPrimaryFixed, stop.onPrimaryFixed, fraction),
        onPrimaryFixedVariant = lerp(start.onPrimaryFixedVariant, stop.onPrimaryFixedVariant, fraction),
        secondaryFixed = lerp(start.secondaryFixed, stop.secondaryFixed, fraction),
        secondaryFixedDim = lerp(start.secondaryFixedDim, stop.secondaryFixedDim, fraction),
        onSecondaryFixed = lerp(start.onSecondaryFixed, stop.onSecondaryFixed, fraction),
        onSecondaryFixedVariant = lerp(start.onSecondaryFixedVariant, stop.onSecondaryFixedVariant, fraction),
        tertiaryFixed = lerp(start.tertiaryFixed, stop.tertiaryFixed, fraction),
        tertiaryFixedDim = lerp(start.tertiaryFixedDim, stop.tertiaryFixedDim, fraction),
        onTertiaryFixed = lerp(start.onTertiaryFixed, stop.onTertiaryFixed, fraction),
        onTertiaryFixedVariant = lerp(start.onTertiaryFixedVariant, stop.onTertiaryFixedVariant, fraction),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val MOTION_SCHEME = MotionScheme.expressive()
