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

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection

/**
 * The room a popup may take: the window less what the system bars, the display cutout, and the keyboard cover. An
 * overflow menu's dropdown is bounded by its height ([heightPx]), a tooltip placed inside it ([safeBounds]). Resolved
 * once for the app ([LocalPopupRoom]) rather than by every menu and tooltip, a song card's among them, which would each
 * look up the insets while composing for a value only an open popup uses; and read only while the popup is measured or
 * placed, since the window's size and the keyboard's inset change on every frame of a resize or of the keyboard's
 * animation.
 *
 * Holds the [WindowInfo] itself rather than its size, which read where this is made would recompose the app on every
 * frame of a resize, and the insets as a state, since the platforms other than Android hand out new ones on every
 * composition and this has to stay one instance.
 */
@Stable
internal class PopupRoom(
    private val windowInfo: WindowInfo,
    private val insets: State<WindowInsets>,
) {
    fun heightPx(density: Density) = (windowInfo.containerSize.height - insets.value.getTop(density) - insets.value.getBottom(density)).coerceAtLeast(0)

    /** The part of a popup's [windowSize] that no inset covers, in the coordinates a popup is placed in. */
    fun safeBounds(windowSize: IntSize, density: Density, layoutDirection: LayoutDirection) = insets.value.let { insets ->
        IntRect(
            left = insets.getLeft(density, layoutDirection),
            top = insets.getTop(density),
            right = windowSize.width - insets.getRight(density, layoutDirection),
            bottom = windowSize.height - insets.getBottom(density),
        )
    }
}

/**
 * The [PopupRoom] of the app, provided next to the overlay state. Its own local rather than a part of that state: the
 * overlay state is the view model's and outlives an Android activity, and the insets belong to the view that resolved
 * them. Null outside the app's composition, where each popup resolves its own.
 */
internal val LocalPopupRoom = staticCompositionLocalOf<PopupRoom?> { null }

/** The [PopupRoom] of the window this is composed in. */
@Composable
internal fun rememberPopupRoom(): PopupRoom {
    val windowInfo = LocalWindowInfo.current
    // Both getters are called on every composition, which is what keeps them registered on Android.
    val safeDrawing = WindowInsets.safeDrawing
    val ime = WindowInsets.ime
    val insets = rememberUpdatedState(remember(safeDrawing, ime) { safeDrawing.union(ime) })
    return remember(windowInfo, insets) { PopupRoom(windowInfo, insets) }
}
