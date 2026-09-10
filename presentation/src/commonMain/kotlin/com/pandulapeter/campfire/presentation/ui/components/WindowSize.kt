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

    /** Whether the app bar of the song details screen has room for the steppers, or they belong in a bottom sheet. */
    val usesInlineSongControls get() = this == EXPANDED

    companion object {
        fun fromWidth(width: Dp) = when {
            width < 600.dp -> COMPACT
            width < 840.dp -> MEDIUM
            else -> EXPANDED
        }
    }
}
