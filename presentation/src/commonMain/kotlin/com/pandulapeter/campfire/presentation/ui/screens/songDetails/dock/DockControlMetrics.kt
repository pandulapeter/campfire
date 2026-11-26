/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails.dock

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.lerp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp

/**
 * How far the dock's tall controls (the instrument card and the tempo chip beside the diagrams) are made smaller, from 0
 * (full size) to 1 (a narrow window's, [isCompact], `DockLayout.areControlsCompact`), so that on a phone the two cards
 * leave the diagrams the room. Animated, so that a window resized across [DOCK_COMPACT_WINDOW_WIDTH] eases the cards
 * from one size to the other rather than snapping.
 */
@Composable
internal fun dockCompactness(isCompact: Boolean) = animateFloatAsState(if (isCompact) 1f else 0f, MaterialTheme.motionScheme.defaultSpatialSpec()).value

/** How wide a tall control's round button (the tempo chip's play and stop, the instrument card's tuner) is at [compactness]. */
internal fun dockRoundButtonSize(compactness: Float) = lerp(44.dp, 32.dp, compactness)

/**
 * How far a tall control's round button is from its end at [compactness]: as far as from its top and bottom in a row of
 * small diagrams at full size, and as far as its text is from its start where the window is narrow, keeping the card
 * symmetric across.
 */
internal fun dockTallButtonPadding(compactness: Float) = lerp(10.dp, DOCK_COMPACT_CHIP_PADDING, compactness)

/** How far a tall control keeps its text from its start and from its round button at [compactness]. */
internal fun dockTallChipPadding(compactness: Float) = lerp(DOCK_CHIP_PADDING, DOCK_COMPACT_CHIP_PADDING, compactness)

/** [large] one size down at [compactness] ([small]), the tall controls' text in a narrow window. */
internal fun dockTallTextStyle(large: TextStyle, small: TextStyle, compactness: Float) = lerp(large, small, compactness)

/** How far the dock's controls keep their text from their sides, and from a round button after it. */
internal val DOCK_CHIP_PADDING = 12.dp

/** The same in a narrow window, where the tall controls are made smaller. */
private val DOCK_COMPACT_CHIP_PADDING = 8.dp

/** How narrow a window is where the dock's tall controls are made smaller: an upright phone's, below a navigation rail's. */
internal val DOCK_COMPACT_WINDOW_WIDTH = 600.dp
