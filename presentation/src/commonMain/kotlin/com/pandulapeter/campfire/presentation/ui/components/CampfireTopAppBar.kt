/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * Top app bar that gets a tonal tint and a shadow as soon as content scrolls underneath it, so that the bar stays
 * visually separated from the list. The screen's scrollable content must be hooked up with
 * `Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)`.
 *
 * A screen that needs more than one row of controls puts the rest in [bottomContent], which is drawn inside the same
 * surface and under the same shadow: the editor's toolbar is part of the bar rather than a strip floating under it,
 * so the whole thing tints and lifts together as the text scrolls beneath it.
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
    actions: @Composable RowScope.() -> Unit = {},
    bottomContent: @Composable ColumnScope.() -> Unit = {},
) {
    val isOverlapped = scrollBehavior.state.overlappedFraction > 0.01f
    val overlapProgress by animateFloatAsState(
        targetValue = if (isOverlapped) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
    )
    Surface(
        modifier = modifier.zIndex(1f), // Draw the shadow over the content that follows in the column.
        color = lerp(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surfaceContainer, overlapProgress),
        shadowElevation = OVERLAPPED_ELEVATION * overlapProgress,
    ) {
        Column {
            TopAppBar(
                title = title,
                navigationIcon = navigationIcon,
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
                scrollBehavior = scrollBehavior,
            )
            bottomContent()
        }
    }
}

/**
 * A top level screen: its app bar across the whole of the window, and everything else under it and next to the
 * navigation rail, which the shell lays out under the screens and starts below the height of this bar.
 *
 * Only the two parts are opaque, and neither covers the rail's column below the bar. The shell draws the rail under
 * the screens, so it stays where it is while they cross fade, and a screen covering its column - even with nothing
 * but a transparent layout - would take every tap meant for the rail.
 *
 * @param railWidth How much of the window the navigation rail takes from the start edge, or zero next to a bar.
 * @param content What goes under the app bar, laid out on an opaque surface that blocks touches from reaching the
 *   screen it covers during a transition.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TopLevelScreenLayout(
    modifier: Modifier = Modifier,
    railWidth: Dp,
    appBar: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) = Column(
    modifier = modifier.fillMaxSize(),
) {
    appBar()
    Surface(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(start = railWidth)
            // The rail covers the insets on its own edge, so nothing inside should apply them a second time.
            .consumeWindowInsets(PaddingValues(start = railWidth)),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(content = content)
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
    scrollableState: ScrollableState,
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
