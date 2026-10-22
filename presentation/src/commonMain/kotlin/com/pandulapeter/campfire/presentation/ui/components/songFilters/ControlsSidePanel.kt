/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components.songFilters

import com.pandulapeter.campfire.presentation.ui.chrome.TopLevelScreenSurface
import com.pandulapeter.campfire.presentation.ui.chrome.contentEdges
import com.pandulapeter.campfire.presentation.ui.chrome.withMinTopEdge
import com.pandulapeter.campfire.presentation.ui.components.only
import com.pandulapeter.campfire.presentation.ui.components.appBar.SearchableTopAppBar
import com.pandulapeter.campfire.presentation.ui.components.list.SIDE_PANEL_WIDTH
import com.pandulapeter.campfire.presentation.ui.components.list.hasRoomForSidePanel
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.width
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp

/**
 * The [SongFilters] in a panel next to the list, shown on screens that are wide enough for it, see [hasRoomForSidePanel]. The list screens' app bar spans the list alone (see
 * [SearchableTopAppBar]), so the panel reaches the top of the screen beside it. It stands on the navigation chrome's
 * color, so that its controls read as the screen's tools rather than as part of the list, and like the rail it reaches
 * the top of the window: [TopLevelScreenSurface] keeps the screen below the status bar, so the panel reaches back up
 * over that room and keeps its controls below it with its own padding instead.
 *
 * @param isAnimated Whether the panel slides in, false while the library is still arriving: the panel appears with the
 *   library's first tag, and sliding it in then would squeeze every card on screen as the app opens.
 * @param content The controls themselves, handed the modifier that gives the panel its size and the insets the panel
 *   is responsible for.
 */
@Composable
internal fun ControlsSidePanel(
    isVisible: Boolean,
    isAnimated: Boolean,
    contentPadding: PaddingValues,
    content: @Composable (modifier: Modifier, contentPadding: PaddingValues) -> Unit,
) {
    val density = LocalDensity.current
    val topEdge = WindowInsets.contentEdges.only(WindowInsetsSides.Top).withMinTopEdge.getTop(density)
    AnimatedVisibility(
        visible = isVisible,
        modifier = Modifier.reachingUp(topEdge),
        enter = if (isAnimated) expandHorizontally() + fadeIn() else EnterTransition.None,
        exit = shrinkHorizontally() + fadeOut(),
    ) {
        val endPadding = contentPadding.calculateEndPadding(LocalLayoutDirection.current)
        content(
            Modifier.width(SIDE_PANEL_WIDTH + endPadding).fillMaxHeight().background(NavigationBarDefaults.containerColor),
            PaddingValues(
                end = endPadding,
                top = SIDE_PANEL_TOP_PADDING + with(density) { topEdge.toDp() },
                bottom = contentPadding.calculateBottomPadding() + SIDE_PANEL_BOTTOM_PADDING,
            ),
        )
    }
}

/**
 * Lays [this] out [distance] pixels taller, the extra above its own top, while its parent still sees the size it was
 * given. Applied outside the [AnimatedVisibility], whose expansion clips to its own bounds.
 */
private fun Modifier.reachingUp(distance: Int) = layout { measurable, constraints ->
    val placeable = measurable.measure(
        if (constraints.hasBoundedHeight) {
            constraints.copy(minHeight = constraints.minHeight + distance, maxHeight = constraints.maxHeight + distance)
        } else {
            constraints
        },
    )
    layout(placeable.width, (placeable.height - distance).coerceAtLeast(0)) { placeable.place(0, -distance) }
}

/**
 * The padding of the content shown next to a [ControlsSidePanel]: while the panel is visible, the end inset belongs
 * to the panel.
 */
internal fun PaddingValues.besideSidePanel(isSidePanelVisible: Boolean) =
    if (isSidePanelVisible) only(start = true, top = true, bottom = true) else this

private val SIDE_PANEL_TOP_PADDING = 16.dp
private val SIDE_PANEL_BOTTOM_PADDING = 16.dp
