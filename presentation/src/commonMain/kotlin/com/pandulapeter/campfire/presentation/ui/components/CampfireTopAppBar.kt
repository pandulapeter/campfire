package com.pandulapeter.campfire.presentation.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * Top app bar that gets a tonal tint and a shadow as soon as content scrolls underneath it, so that the bar stays
 * visually separated from the list. The screen's scrollable content must be hooked up with
 * `Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)`.
 *
 * The background is drawn by the wrapping [Surface] and the bar itself is transparent, because [TopAppBar] cross
 * fades its own container color with a spring of its own. That spring would chase the color scheme while it is
 * animating between the light and the dark theme, leaving the bar visibly trailing behind the rest of the screen.
 * Only the overlap state is animated here, and the two colors it interpolates follow the theme immediately.
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
    val overlapProgress by animateFloatAsState(
        targetValue = if (isOverlapped) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()
    )
    Surface(
        modifier = modifier.zIndex(1f), // Draw the shadow over the content that follows in the column.
        color = lerp(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surfaceContainer, overlapProgress),
        shadowElevation = OVERLAPPED_ELEVATION * overlapProgress
    ) {
        TopAppBar(
            title = title,
            navigationIcon = navigationIcon,
            actions = actions,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent
            ),
            scrollBehavior = scrollBehavior
        )
    }
}

/**
 * Programmatic scrolls (jumping to the top on a new query, dragging the fast scroller) bypass the nested scroll
 * connection, so the overlap state of the app bar is corrected from the list here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun KeepTopAppBarInSync(
    scrollBehavior: TopAppBarScrollBehavior,
    scrollableState: ScrollableState
) = LaunchedEffect(scrollBehavior, scrollableState) {
    snapshotFlow { scrollableState.canScrollBackward }.collect { canScrollBackward ->
        if (!canScrollBackward) {
            scrollBehavior.state.contentOffset = 0f
        } else if (scrollBehavior.state.contentOffset == 0f) {
            scrollBehavior.state.contentOffset = scrollBehavior.state.heightOffsetLimit
        }
    }
}

private val OVERLAPPED_ELEVATION = 4.dp
