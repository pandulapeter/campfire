package com.pandulapeter.campfire.shared.ui.components

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
