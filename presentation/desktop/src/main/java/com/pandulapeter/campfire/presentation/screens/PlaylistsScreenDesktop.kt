package com.pandulapeter.campfire.presentation.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.shared.ui.screenComponents.playlists.PlaylistsPlaceholder

@Composable
internal fun PlaylistsScreensDesktop(
    modifier: Modifier = Modifier
) = PlaylistsPlaceholder(
    modifier = modifier.padding(8.dp)
)