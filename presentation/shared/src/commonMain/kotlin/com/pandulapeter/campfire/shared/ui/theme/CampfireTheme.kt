package com.pandulapeter.campfire.shared.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import com.pandulapeter.campfire.data.model.domain.UserPreferences

/**
 * Material 3 Expressive theme of the app. Switching between the light and dark color schemes is animated.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CampfireTheme(
    uiMode: UserPreferences.UiMode?,
    content: @Composable () -> Unit
) = MaterialExpressiveTheme(
    colorScheme = (if (uiMode.isDarkTheme()) CampfireColorSchemes.dark else CampfireColorSchemes.light).animated(),
    motionScheme = MotionScheme.expressive(),
    content = content
)

/**
 * Resolves whether the given user preference results in a dark theme, falling back to the system setting.
 */
@Composable
fun UserPreferences.UiMode?.isDarkTheme() = when (this) {
    UserPreferences.UiMode.LIGHT -> false
    UserPreferences.UiMode.DARK -> true
    UserPreferences.UiMode.SYSTEM_DEFAULT, null -> isSystemInDarkTheme()
}

@Composable
private fun ColorScheme.animated() = copy(
    primary = primary.animate(),
    onPrimary = onPrimary.animate(),
    primaryContainer = primaryContainer.animate(),
    onPrimaryContainer = onPrimaryContainer.animate(),
    inversePrimary = inversePrimary.animate(),
    secondary = secondary.animate(),
    onSecondary = onSecondary.animate(),
    secondaryContainer = secondaryContainer.animate(),
    onSecondaryContainer = onSecondaryContainer.animate(),
    tertiary = tertiary.animate(),
    onTertiary = onTertiary.animate(),
    tertiaryContainer = tertiaryContainer.animate(),
    onTertiaryContainer = onTertiaryContainer.animate(),
    background = background.animate(),
    onBackground = onBackground.animate(),
    surface = surface.animate(),
    onSurface = onSurface.animate(),
    surfaceVariant = surfaceVariant.animate(),
    onSurfaceVariant = onSurfaceVariant.animate(),
    surfaceTint = surfaceTint.animate(),
    inverseSurface = inverseSurface.animate(),
    inverseOnSurface = inverseOnSurface.animate(),
    error = error.animate(),
    onError = onError.animate(),
    errorContainer = errorContainer.animate(),
    onErrorContainer = onErrorContainer.animate(),
    outline = outline.animate(),
    outlineVariant = outlineVariant.animate(),
    scrim = scrim.animate(),
    surfaceBright = surfaceBright.animate(),
    surfaceDim = surfaceDim.animate(),
    surfaceContainer = surfaceContainer.animate(),
    surfaceContainerHigh = surfaceContainerHigh.animate(),
    surfaceContainerHighest = surfaceContainerHighest.animate(),
    surfaceContainerLow = surfaceContainerLow.animate(),
    surfaceContainerLowest = surfaceContainerLowest.animate()
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Color.animate(): Color {
    val color by animateColorAsState(targetValue = this, animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec())
    return color
}
