package com.pandulapeter.campfire.presentation.android.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pandulapeter.campfire.shared.ui.CampfireViewModelStateHolder
import com.pandulapeter.campfire.shared.ui.screenComponents.playlists.PlaylistsPlaceholder

@Composable
internal fun PlaylistsScreenAndroid(
    modifier: Modifier = Modifier,
    stateHolder: CampfireViewModelStateHolder
) = PlaylistsPlaceholder(
    modifier = modifier,
    uiStrings = stateHolder.uiStrings.value
)