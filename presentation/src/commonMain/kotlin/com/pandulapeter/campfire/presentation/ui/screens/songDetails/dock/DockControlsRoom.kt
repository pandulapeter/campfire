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

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.SETLIST_CONTROLS_MIN_WIDTH

/**
 * How wide the dock's controls are in one line, before they are laid out: the instrument card ([card], none without one)
 * and the tempo chip ([tempoChip], none without one), each with the gap that parts it from what stands beside it.
 */
internal class DockControlWidths(val card: Dp, val tempoChip: Dp) {

    /** What a row [rowWidth] wide leaves between them, their gaps taken. */
    fun roomBetween(rowWidth: Dp) = rowWidth - (if (card > 0.dp) card + DOCK_ITEM_GAP else 0.dp) - (if (tempoChip > 0.dp) tempoChip + DOCK_ITEM_GAP else 0.dp)
}

/**
 * Whether the song's scale chips, [scalesWidth] wide in one line (null before its page has drawn them, when they are
 * taken to fit), fit between the controls ([widths]) in the dock's one row [rowWidth] wide: where they do not, a row
 * of their own shows them whole rather than a few at a time through a gap (`dockLayoutOf`).
 */
internal fun scalesFitBesideControls(widths: DockControlWidths, rowWidth: Dp, scalesWidth: Dp?) =
    scalesWidth == null || scalesWidth <= widths.roomBetween(rowWidth)

/**
 * Whether the setlist's pager bar, [rowWidth] wide inside its padding, holds the dock's controls ([widths]) around its
 * own: always the instrument card alone, which is cut short where it must be, and the card and the tempo chip where
 * their names and the setlist's controls all fit ([SETLIST_CONTROLS_MIN_WIDTH]).
 */
internal fun controlsFitPagerBar(widths: DockControlWidths, rowWidth: Dp) =
    widths.tempoChip == 0.dp || widths.roomBetween(rowWidth) >= SETLIST_CONTROLS_MIN_WIDTH
