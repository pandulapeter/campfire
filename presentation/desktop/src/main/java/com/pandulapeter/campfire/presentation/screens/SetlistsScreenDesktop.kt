package com.pandulapeter.campfire.presentation.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.shared.ui.CampfireViewModelStateHolder
import com.pandulapeter.campfire.shared.ui.screenComponents.setlists.SetlistsPlaceholder

@Composable
internal fun SetlistsScreensDesktop(
    modifier: Modifier = Modifier,
    stateHolder: CampfireViewModelStateHolder
) = SetlistsPlaceholder(
    modifier = modifier.padding(8.dp),
    uiStrings = stateHolder.uiStrings.value
)