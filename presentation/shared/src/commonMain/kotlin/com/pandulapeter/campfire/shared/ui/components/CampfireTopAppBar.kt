package com.pandulapeter.campfire.shared.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * Top app bar that gets a tonal tint and a shadow as soon as content scrolls underneath it, so that the bar stays
 * visually separated from the list. The screen's scrollable content must be hooked up with
 * `Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)`.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun CampfireTopAppBar(
    modifier: Modifier = Modifier,
    scrollBehavior: TopAppBarScrollBehavior,
    title: @Composable () -> Unit,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {}
) {
    val isOverlapped = scrollBehavior.state.overlappedFraction > 0.01f
    val elevation by animateDpAsState(
        targetValue = if (isOverlapped) OVERLAPPED_ELEVATION else 0.dp,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()
    )
    Surface(
        modifier = modifier.zIndex(1f), // Draw the shadow over the content that follows in the column.
        color = Color.Transparent,
        shadowElevation = elevation
    ) {
        TopAppBar(
            title = title,
            navigationIcon = navigationIcon,
            actions = actions,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            scrollBehavior = scrollBehavior
        )
    }
}

private val OVERLAPPED_ELEVATION = 4.dp
