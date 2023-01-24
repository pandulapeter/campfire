package com.pandulapeter.campfire.presentation.android.catalogue

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.shared.ui.catalogue.theme.CampfireColors


@Composable
internal fun CampfireAndroidTheme(
    uiMode: UserPreferences.UiMode?,
    content: @Composable () -> Unit
) = MaterialTheme(
    colors = when (uiMode) {
        UserPreferences.UiMode.LIGHT -> CampfireColors.campfireLightColors
        UserPreferences.UiMode.DARK -> CampfireColors.campfireDarkColors
        UserPreferences.UiMode.SYSTEM_DEFAULT, null -> if (isSystemInDarkTheme()) CampfireColors.campfireDarkColors else CampfireColors.campfireLightColors
    },
    content = content
)