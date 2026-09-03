package com.pandulapeter.campfire.shared.ui.platform

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

internal actual val isDesktopPlatform = true

@Composable
internal actual fun BoxScope.PlatformVerticalScrollbar(
    listState: LazyListState,
    modifier: Modifier
) = VerticalScrollbar(
    modifier = modifier.align(Alignment.CenterEnd).fillMaxHeight(),
    adapter = rememberScrollbarAdapter(listState)
)
