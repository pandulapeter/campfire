package com.pandulapeter.campfire.presentation.android.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pandulapeter.campfire.shared.ui.CampfireViewModelStateHolder
import com.pandulapeter.campfire.shared.ui.screenComponents.setlists.SetlistsPlaceholder

@Composable
internal fun SetlistsScreenAndroid(
    modifier: Modifier = Modifier,
    stateHolder: CampfireViewModelStateHolder
) = SetlistsPlaceholder(
    modifier = modifier,
    uiStrings = stateHolder.uiStrings.value
)