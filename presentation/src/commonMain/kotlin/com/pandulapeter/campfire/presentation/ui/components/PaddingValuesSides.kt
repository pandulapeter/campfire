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

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Some of the sides of [source], each asked of it at the moment it is used rather than when this is made, with
 * [extraTop] added above.
 *
 * The paddings the app hands its screens follow the keyboard (see `CampfireApp`), and they are only cheap for as
 * long as nobody takes them apart while composing: `PaddingValues(bottom = padding.calculateBottomPadding())`
 * reads the keyboard right there, and the screen doing it is recomposed on every frame the keyboard moves for. A
 * list or a `Modifier.padding` given this instead asks while it is being laid out.
 */
@Immutable
private data class PaddingValuesSides(
    private val source: PaddingValues,
    private val hasStart: Boolean,
    private val hasTop: Boolean,
    private val hasEnd: Boolean,
    private val hasBottom: Boolean,
    private val extraTop: Dp,
) : PaddingValues {

    override fun calculateLeftPadding(layoutDirection: LayoutDirection) =
        if (if (layoutDirection == LayoutDirection.Ltr) hasStart else hasEnd) source.calculateLeftPadding(layoutDirection) else 0.dp

    override fun calculateTopPadding() = (if (hasTop) source.calculateTopPadding() else 0.dp) + extraTop

    override fun calculateRightPadding(layoutDirection: LayoutDirection) =
        if (if (layoutDirection == LayoutDirection.Ltr) hasEnd else hasStart) source.calculateRightPadding(layoutDirection) else 0.dp

    override fun calculateBottomPadding() = if (hasBottom) source.calculateBottomPadding() else 0.dp
}

/** The named sides of these paddings and nothing on the others, see [PaddingValuesSides]. */
internal fun PaddingValues.only(
    start: Boolean = false,
    top: Boolean = false,
    end: Boolean = false,
    bottom: Boolean = false,
    extraTop: Dp = 0.dp,
): PaddingValues = PaddingValuesSides(
    source = this,
    hasStart = start,
    hasTop = top,
    hasEnd = end,
    hasBottom = bottom,
    extraTop = extraTop,
)
