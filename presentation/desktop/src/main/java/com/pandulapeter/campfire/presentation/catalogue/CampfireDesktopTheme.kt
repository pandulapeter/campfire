package com.pandulapeter.campfire.presentation.catalogue

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.shared.ui.catalogue.CampfireColors

private val campfireLightColors by lazy {
    lightColors(
        primary = CampfireColors.colorCampfireOrange,
        secondary = CampfireColors.colorCampfireOrange
    )
}

private val campfireDarkColors by lazy {
    darkColors(
        primary = CampfireColors.colorCampfireOrange,
        secondary = CampfireColors.colorCampfireOrange
    )
}

@Composable
internal fun CampfireDesktopTheme(
    uiMode: UserPreferences.UiMode?,
    content: @Composable () -> Unit
) = MaterialTheme(
    colors = when (uiMode) {
        UserPreferences.UiMode.LIGHT -> campfireLightColors
        UserPreferences.UiMode.DARK -> campfireDarkColors
        UserPreferences.UiMode.SYSTEM_DEFAULT, null -> if (isSystemInDarkTheme()) campfireDarkColors else campfireLightColors
    },
    content = content
)