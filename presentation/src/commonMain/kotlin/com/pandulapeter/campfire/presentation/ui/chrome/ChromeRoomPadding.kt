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
import androidx.compose.runtime.Stable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * The room the navigation chrome takes out of the window ([NavigationChromeSize]) as a padding: [start] for the rail
 * and [bottom] for the bar. They are read when the padding is used rather than when it is made, so that a handover
 * between two kinds of chrome lays the screens next to it out again on each of its frames rather than composing them
 * again; two of these are equal for as long as they read the same two sizes.
 */
@Stable
internal class ChromeRoomPadding(
    private val start: () -> Dp,
    private val bottom: () -> Dp,
) : PaddingValues {

    override fun calculateLeftPadding(layoutDirection: LayoutDirection) = if (layoutDirection == LayoutDirection.Ltr) start() else 0.dp

    override fun calculateTopPadding() = 0.dp

    override fun calculateRightPadding(layoutDirection: LayoutDirection) = if (layoutDirection == LayoutDirection.Ltr) 0.dp else start()

    override fun calculateBottomPadding() = bottom()

    override fun equals(other: Any?) = other is ChromeRoomPadding && start === other.start && bottom === other.bottom

    override fun hashCode() = 31 * start.hashCode() + bottom.hashCode()
}

/** A side of the window the chrome takes nothing from, one instance so that the paddings reading it stay equal. */
internal val NoChromeRoom: () -> Dp = { 0.dp }
