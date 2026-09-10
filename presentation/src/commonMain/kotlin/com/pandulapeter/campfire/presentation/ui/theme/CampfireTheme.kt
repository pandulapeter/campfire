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

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
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
 * Material 3 Expressive theme of the app. Switching between the light and dark color schemes is animated.
 *
 * The two schemes are cross faded with a single progress value rather than by animating the color roles one by one:
 * a spring reaches its visibility threshold sooner the shorter the distance it has to cover, so the roles that
 * barely differ between the two schemes would snap over immediately while the rest were still on their way, and the
 * screen would appear to change in pieces.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CampfireTheme(
    uiMode: UserPreferences.UiMode?,
    content: @Composable () -> Unit
) {
    // Until the stored preferences are loaded the theme is only a guess based on the system setting. Correcting that
    // guess is not a theme change the user made, so it must not be animated - otherwise every launch that starts
    // with the theme unset (or with a preference that differs from the system one) cross fades the whole UI.
    val arePreferencesLoaded = uiMode != null
    var isAnimated by remember { mutableStateOf(false) }
    LaunchedEffect(arePreferencesLoaded) { isAnimated = arePreferencesLoaded }
    val progress by animateFloatAsState(
        targetValue = if (uiMode.isDarkTheme()) 1f else 0f,
        animationSpec = if (isAnimated) MOTION_SCHEME.defaultEffectsSpec() else snap()
    )
    MaterialExpressiveTheme(
        // At rest the schemes are handed over as they are, so that the composables reading them are not invalidated
        // by a new instance on every recomposition.
        colorScheme = when (progress) {
            0f -> CampfireColorSchemes.light
            1f -> CampfireColorSchemes.dark
            else -> lerp(CampfireColorSchemes.light, CampfireColorSchemes.dark, progress)
        },
        motionScheme = MOTION_SCHEME,
        content = content
    )
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

private fun lerp(start: ColorScheme, stop: ColorScheme, fraction: Float) = start.copy(
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val MOTION_SCHEME = MotionScheme.expressive()
