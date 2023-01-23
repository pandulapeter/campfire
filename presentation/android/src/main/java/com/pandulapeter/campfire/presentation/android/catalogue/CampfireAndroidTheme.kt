package com.pandulapeter.campfire.presentation.android.catalogue

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.shared.ui.catalogue.CampfireColors


@Composable
internal fun CampfireAndroidTheme(
    uiMode: UserPreferences.UiMode?,
    shouldUseDynamicColors: Boolean,
    content: @Composable () -> Unit
) {
    val areDynamicColorsSupported = shouldUseDynamicColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    @Composable
    fun createLightColorScheme() = if (areDynamicColorsSupported) {
        dynamicLightColorScheme(LocalContext.current)
    } else {
        lightColorScheme(
            primary = CampfireColors.colorCampfireOrange,
            secondary = CampfireColors.colorCampfireOrange
        )
    }

    @Composable
    fun createDarkColorScheme() = if (areDynamicColorsSupported) {
        dynamicDarkColorScheme(LocalContext.current)
    } else {
        darkColorScheme(
            primary = CampfireColors.colorCampfireOrange,
            secondary = CampfireColors.colorCampfireOrange
        )
    }

    MaterialTheme(
        colorScheme = when (uiMode) {
            UserPreferences.UiMode.LIGHT -> createLightColorScheme()
            UserPreferences.UiMode.DARK -> createDarkColorScheme()
            UserPreferences.UiMode.SYSTEM_DEFAULT, null -> if (isSystemInDarkTheme()) createDarkColorScheme() else createLightColorScheme()
        },
        content = content
    )
}