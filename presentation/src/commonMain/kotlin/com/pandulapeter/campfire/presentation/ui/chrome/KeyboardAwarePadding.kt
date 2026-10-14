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

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * The window insets a screen hands to its scrolling content: what the system bars and the chrome leave of the
 * edges, and at the bottom the keyboard wherever it reaches higher than that.
 *
 * The keyboard is asked about when the padding is used rather than when it is made. Its inset is animated, so a
 * composition that reads it runs again on every frame the keyboard is moving for, and the composition these are
 * made in holds the whole app - the navigation display, every screen on it and the song list with them. What
 * uses a padding is a layout, and a layout that reads a state that has changed is only laid out again.
 *
 * @param coveredHeight How much of the keyboard's height is taken by chrome it slides over rather than pushes
 *   away, which is the navigation bar's.
 * @param ime Held as a state because the platforms other than Android hand out a new instance on every
 *   composition, and two of these have to be equal for as long as nothing but the keyboard has moved, or every
 *   screen would be recomposed whenever this composition is.
 */
@Stable
internal class KeyboardAwarePadding(
    private val start: Dp,
    private val end: Dp,
    private val bottom: Dp,
    private val coveredHeight: Dp,
    private val ime: State<WindowInsets>,
    private val density: Density,
) : PaddingValues {

    override fun calculateLeftPadding(layoutDirection: LayoutDirection) = if (layoutDirection == LayoutDirection.Ltr) start else end

    override fun calculateTopPadding() = 0.dp

    override fun calculateRightPadding(layoutDirection: LayoutDirection) = if (layoutDirection == LayoutDirection.Ltr) end else start

    override fun calculateBottomPadding() = maxOf(bottom, with(density) { ime.value.getBottom(this).toDp() } - coveredHeight)

    override fun equals(other: Any?) = other is KeyboardAwarePadding &&
            start == other.start &&
            end == other.end &&
            bottom == other.bottom &&
            coveredHeight == other.coveredHeight &&
            ime === other.ime &&
            density == other.density

    override fun hashCode() = listOf(start, end, bottom, coveredHeight, density).hashCode()
}
