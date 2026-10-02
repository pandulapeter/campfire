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

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Material window size class buckets, based on the width of the window.
 */
internal enum class WindowSize {
    COMPACT, MEDIUM, EXPANDED;

    val usesNavigationRail get() = this != COMPACT

    companion object {
        fun fromWidth(width: Dp) = when {
            width < 600.dp -> COMPACT
            width < 840.dp -> MEDIUM
            else -> EXPANDED
        }
    }
}

/**
 * Below this height a window is short: a phone on its side, or the smallest phone with the keyboard up. Short windows
 * let headers scroll away, slim their bars and take the status bar back while typing, since what they would pin
 * otherwise leaves no room for the field or the list it is there for.
 */
internal val SHORT_WINDOW_HEIGHT = 480.dp
