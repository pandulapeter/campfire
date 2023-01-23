package com.pandulapeter.campfire.presentation.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.shared.ui.screenComponents.songs.SongsPlaceholder

@Composable
internal fun SongsScreensDesktop(
    modifier: Modifier = Modifier
) = SongsPlaceholder(
    modifier = modifier.padding(8.dp)
)