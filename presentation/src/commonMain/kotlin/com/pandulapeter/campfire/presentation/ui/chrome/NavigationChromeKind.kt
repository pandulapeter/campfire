/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.chrome

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.ui.components.WindowSize
import com.pandulapeter.campfire.presentation.ui.components.list.hasRoomForSidePanel

/**
 * The three shapes the navigation chrome takes: the bar under 600dp, the rail above, and the expanded rail - the one
 * with each label beside its icon rather than under it - where the window has the room for it. That rail is well over
 * a hundred dp wider than the collapsed one, and the lists next to it are what pays for it, so it is only used where
 * the list screens still keep their filter side panel beside it (see [navigationChromeKind]).
 */
internal enum class NavigationChromeKind {
    BAR,
    RAIL,
    EXPANDED_RAIL;

    val isRail get() = this != BAR
}

/**
 * What the navigation chrome takes out of the window: [railWidth] and [barHeight] as they are on this frame - one of
 * them nothing, and both something while the chrome changes shape - and [settledRailWidth], what the rail will take
 * once it has arrived. Anything a screen has to decide once is decided from the settled one, so that the column
 * counts do not change a dozen times as the chrome moves.
 *
 * The two that move are read when they are used, by the layouts the screens are inset by, rather than handed over as
 * values: they change on every frame of a handover, and the composition these are handed to holds the whole app. The
 * two readers stay the same instances for as long as the scaffold does, so a size is equal to the one before it for as
 * long as nothing but the handover has moved.
 */
internal data class NavigationChromeSize(
    val railWidth: () -> Dp,
    val barHeight: () -> Dp,
    val settledRailWidth: Dp,
)

/**
 * Whether a window this wide has the room for the expanded navigation rail, with the Songs screen's cards
 * [songColumnWidth] wide (its own [com.pandulapeter.campfire.presentation.ui.components.list.minSongColumnWidth], the one
 * `ListLayout` decides its side panel from). Deciding it from anything but the list screens' own side panel would have
 * the panel come, go and come again as a window is widened past both thresholds.
 *
 * Material decides the expanded rail's width from its items, [EXPANDED_NAVIGATION_RAIL_MIN_WIDTH] being where it
 * starts; the four labels here fit inside that in every language the app speaks.
 */
internal fun navigationChromeKind(windowWidth: Dp, songColumnWidth: Dp) = when {
    !WindowSize.fromWidth(windowWidth).usesNavigationRail -> NavigationChromeKind.BAR
    hasRoomForSidePanel(windowWidth - EXPANDED_NAVIGATION_RAIL_MIN_WIDTH - NAVIGATION_RAIL_CONTENT_GAP, songColumnWidth) -> NavigationChromeKind.EXPANDED_RAIL
    else -> NavigationChromeKind.RAIL
}

internal val EXPANDED_NAVIGATION_RAIL_MIN_WIDTH = 220.dp

/**
 * The content padding the screens keep from the rail on top of their own: the rail stands on a color of its own, and
 * content that starts right at its edge reads as pressed against it. A padding rather than room taken from the
 * window, so that what scrolls sideways - the Settings pages - still slides under the rail.
 */
internal val NAVIGATION_RAIL_CONTENT_GAP = 8.dp
