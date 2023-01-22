package com.pandulapeter.campfire.shared.ui.catalogue

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.pandulapeter.campfire.data.model.domain.UserPreferences

private val colorBrand = Color(245, 124, 0)

private val campfireDarkColors = darkColors(
    primary = colorBrand,
    secondary = colorBrand
)

private val campfireLightColors = lightColors(
    primary = colorBrand,
    secondary = colorBrand
)

@Composable
fun CampfireTheme(
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