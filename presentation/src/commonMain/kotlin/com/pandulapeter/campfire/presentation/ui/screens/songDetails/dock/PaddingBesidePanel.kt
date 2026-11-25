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

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.runtime.Stable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * The screen's [source] padding for what stands beside the side panel ([DockSidePanel]): its end inset less what of it
 * the panel covers as it is [panelWidth] wide, so that the song's end moves with the panel sliding in rather than by the
 * whole inset at once. Read at use, as the screen's padding is, so a panel moving lays out what stands beside it without
 * composing it again.
 */
@Stable
internal class PaddingBesidePanel(
    private val source: PaddingValues,
    private val panelWidth: () -> Dp,
) : PaddingValues {
    override fun calculateLeftPadding(layoutDirection: LayoutDirection) =
        if (layoutDirection == LayoutDirection.Ltr) source.calculateLeftPadding(layoutDirection) else end(layoutDirection)

    override fun calculateTopPadding() = source.calculateTopPadding()

    override fun calculateRightPadding(layoutDirection: LayoutDirection) =
        if (layoutDirection == LayoutDirection.Ltr) end(layoutDirection) else source.calculateRightPadding(layoutDirection)

    override fun calculateBottomPadding() = source.calculateBottomPadding()

    private fun end(layoutDirection: LayoutDirection) = (source.calculateEndPadding(layoutDirection) - panelWidth()).coerceAtLeast(0.dp)
}
