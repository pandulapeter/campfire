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
 * [extraStart] added at the start, [extraTop] above, [extraEnd] at the end and [extraBottom] below, and at the end and
 * below [deferredExtraEnd] and [deferredExtraBottom] as well, read when the padding is used.
 *
 * The paddings the app hands its screens follow the keyboard (see `CampfireApp`), and they are only cheap for as
 * long as nobody takes them apart while composing: `PaddingValues(bottom = padding.calculateBottomPadding())`
 * reads the keyboard right there, and the screen doing it is recomposed on every frame the keyboard moves for. A
 * list or a `Modifier.padding` given this instead asks while it is being laid out. The deferred extras are for the
 * same kind of value of the screen's own, an animated room such as the fast scroller's column: they are compared by
 * identity, like every function a data class holds, so two compositions handing over the same reader make equal
 * paddings, and a lazy list keeps its measure policy rather than building it again.
 */
@Immutable
private data class PaddingValuesSides(
    private val source: PaddingValues,
    private val hasStart: Boolean,
    private val hasTop: Boolean,
    private val hasEnd: Boolean,
    private val hasBottom: Boolean,
    private val extraStart: Dp,
    private val extraTop: Dp,
    private val extraEnd: Dp,
    private val extraBottom: Dp,
    private val deferredExtraEnd: () -> Dp,
    private val deferredExtraBottom: () -> Dp,
) : PaddingValues {

    override fun calculateLeftPadding(layoutDirection: LayoutDirection) =
        (if (if (layoutDirection == LayoutDirection.Ltr) hasStart else hasEnd) source.calculateLeftPadding(layoutDirection) else 0.dp) +
            if (layoutDirection == LayoutDirection.Rtl) extraEnd + deferredExtraEnd() else extraStart

    override fun calculateTopPadding() = (if (hasTop) source.calculateTopPadding() else 0.dp) + extraTop

    override fun calculateRightPadding(layoutDirection: LayoutDirection) =
        (if (if (layoutDirection == LayoutDirection.Ltr) hasEnd else hasStart) source.calculateRightPadding(layoutDirection) else 0.dp) +
            if (layoutDirection == LayoutDirection.Ltr) extraEnd + deferredExtraEnd() else extraStart

    override fun calculateBottomPadding() = (if (hasBottom) source.calculateBottomPadding() else 0.dp) + extraBottom + deferredExtraBottom()
}

/** The named sides of these paddings and nothing on the others, see [PaddingValuesSides]. */
internal fun PaddingValues.only(
    start: Boolean = false,
    top: Boolean = false,
    end: Boolean = false,
    bottom: Boolean = false,
    extraStart: Dp = 0.dp,
    extraTop: Dp = 0.dp,
    extraEnd: Dp = 0.dp,
    extraBottom: Dp = 0.dp,
    deferredExtraEnd: () -> Dp = NoDeferredExtra,
    deferredExtraBottom: () -> Dp = NoDeferredExtra,
): PaddingValues = PaddingValuesSides(
    source = this,
    hasStart = start,
    hasTop = top,
    hasEnd = end,
    hasBottom = bottom,
    extraStart = extraStart,
    extraTop = extraTop,
    extraEnd = extraEnd,
    extraBottom = extraBottom,
    deferredExtraEnd = deferredExtraEnd,
    deferredExtraBottom = deferredExtraBottom,
)

/** No deferred extra, one instance so that the paddings without one stay equal. */
private val NoDeferredExtra: () -> Dp = { 0.dp }
