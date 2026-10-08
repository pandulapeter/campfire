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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp

/**
 * The [SongFilters] in a panel next to the list, shown on screens that are wide enough for it, see [hasRoomForSidePanel]. The list screens' app bar spans the list alone (see
 * [SearchableTopAppBar]), so the panel reaches the top of the screen beside it.
 *
 * @param content The controls themselves, handed the modifier that gives the panel its size and the insets the panel
 *   is responsible for.
 */
@Composable
internal fun ControlsSidePanel(
    isVisible: Boolean,
    contentPadding: PaddingValues,
    content: @Composable (modifier: Modifier, contentPadding: PaddingValues) -> Unit,
) = AnimatedVisibility(
    visible = isVisible,
    enter = expandHorizontally() + fadeIn(),
    exit = shrinkHorizontally() + fadeOut(),
) {
    val endPadding = contentPadding.calculateEndPadding(LocalLayoutDirection.current)
    // No background and no divider of its own: the panel is part of the screen the bar spans, and a tinted column
    // with an edge reads as a pane of its own under a bar that has not lifted yet, while the list is at its top.
    content(
        Modifier.width(SIDE_PANEL_WIDTH + endPadding).fillMaxHeight(),
        PaddingValues(
            end = endPadding,
            top = SIDE_PANEL_TOP_PADDING,
            bottom = contentPadding.calculateBottomPadding() + SIDE_PANEL_BOTTOM_PADDING,
        ),
    )
}

/**
 * The padding of the content shown next to a [ControlsSidePanel]: while the panel is visible, the end inset belongs
 * to the panel.
 */
internal fun PaddingValues.besideSidePanel(isSidePanelVisible: Boolean) =
    if (isSidePanelVisible) only(start = true, top = true, bottom = true) else this

private val SIDE_PANEL_TOP_PADDING = 16.dp
private val SIDE_PANEL_BOTTOM_PADDING = 16.dp
