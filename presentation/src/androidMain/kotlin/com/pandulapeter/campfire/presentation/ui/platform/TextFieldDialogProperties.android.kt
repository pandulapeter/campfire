/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.platform

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.DialogProperties

/** Keeps the typed dialog's window sizing consistent with the form its content draws. */
internal actual fun textFieldDialogProperties(isFullScreen: Boolean): DialogProperties = DialogProperties(
    usePlatformDefaultWidth = false,
    decorFitsSystemWindows = false,
)

internal actual val isTextFieldDialogWindowFullSize: Boolean = true

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal actual fun Modifier.textFieldDialogInsetsPadding(isFullScreen: Boolean): Modifier = if (isFullScreen) {
    windowInsetsPadding(WindowInsets.safeDrawing)
} else {
    val ime = WindowInsets.ime
    val imeTarget = WindowInsets.imeAnimationTarget
    val density = LocalDensity.current
    // Laid out where the keyboard is going, the small form is drawn where the keyboard is: centered above it, it
    // travels half as far as the keyboard's edge does, so it is drawn that much short of its place until the keyboard
    // gets there. Only the drawing follows the slide, read in the layer, so nothing is measured or laid out again.
    graphicsLayer { translationY = (imeTarget.getBottom(density) - ime.getBottom(density)) / 2f }
        .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout).union(imeTarget))
}
