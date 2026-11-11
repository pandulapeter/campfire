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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupPositionProvider

/**
 * Places a tooltip above its anchor, or below it where there is no room above, and never over the system bars, the
 * display cutout, or the keyboard: Material's own provider keeps a tooltip inside the window only, which on an
 * edge-to-edge screen puts one over the status bar for a button in the app bar, or under a gesture bar's handle.
 */
@Composable
internal fun rememberTooltipPositionProvider(): PopupPositionProvider {
    val popupRoom = LocalPopupRoom.current ?: rememberPopupRoom()
    val density = LocalDensity.current
    return remember(popupRoom, density) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ) = tooltipPosition(
                anchorBounds = anchorBounds,
                safeBounds = popupRoom.safeBounds(windowSize, density, layoutDirection),
                popupSize = popupContentSize,
                spacing = with(density) { TOOLTIP_SPACING.roundToPx() },
            )
        }
    }
}

/**
 * Where a tooltip of [popupSize] goes: centred over [anchorBounds], [spacing] above it, or as far below it where it
 * does not fit above, then moved inside [safeBounds]. A tooltip larger than the safe area keeps to its top start corner,
 * so that the start of its text stays readable.
 */
internal fun tooltipPosition(
    anchorBounds: IntRect,
    safeBounds: IntRect,
    popupSize: IntSize,
    spacing: Int,
): IntOffset {
    val centeredX = anchorBounds.left + (anchorBounds.width - popupSize.width) / 2
    val above = anchorBounds.top - spacing - popupSize.height
    val y = if (above >= safeBounds.top) above else anchorBounds.bottom + spacing
    return IntOffset(
        x = centeredX.coerceAtMost(safeBounds.right - popupSize.width).coerceAtLeast(safeBounds.left),
        y = y.coerceAtMost(safeBounds.bottom - popupSize.height).coerceAtLeast(safeBounds.top),
    )
}

/** Material's own spacing between a tooltip and its anchor. */
private val TOOLTIP_SPACING = 4.dp
