package com.pandulapeter.campfire.shared.ui.platform

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

internal actual val isDesktopPlatform = false

@Composable
internal actual fun BoxScope.PlatformVerticalScrollbar(
    listState: LazyListState,
    modifier: Modifier
) = Unit
