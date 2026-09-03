package com.pandulapeter.campfire.shared.ui.platform

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * True on platforms driven by a pointer rather than touch (no pull to refresh, scrollbars are shown).
 */
internal expect val isDesktopPlatform: Boolean

/**
 * A vertical scrollbar for the given list on platforms that show one; no-op otherwise.
 */
@Composable
internal expect fun BoxScope.PlatformVerticalScrollbar(
    listState: LazyListState,
    modifier: Modifier = Modifier
)
