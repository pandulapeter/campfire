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

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

/**
 * Puts the keyboard away as soon as the list under the search starts moving down, which is what reading the results
 * looks like: the keyboard is only in the way of them by then, and it covers half of a phone's list. Scrolling back
 * up leaves it alone, since that is as likely to be on the way back to the field. Every list that shares a screen, a
 * sheet or a dialog with a text field does this, the two list screens and every modal one alike.
 *
 * Read from the list's own scroll state rather than from the gesture, so that every way the list is moved down counts
 * — a drag, the fling after it, the wheel, the fast scroller — and it fires once as a scroll down begins rather than
 * on every frame of it. The field keeps the focus and the caret, so a tap on it brings the keyboard straight back.
 *
 * @param isEnabled False while the list is covered by a dialog or a sheet, whose keyboard is not this list's to put
 *   away. A list can still move down on its own under one: in a browser that makes the page itself shorter for the
 *   keyboard (Firefox), a list that shrinks past the item that opened the dialog scrolls down to keep that item in
 *   view, which would otherwise close the keyboard the dialog has just brought up.
 */
@Composable
internal fun HideKeyboardWhenScrolledDown(
    scrollableState: ScrollableState,
    isEnabled: Boolean = true,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(scrollableState, keyboardController, isEnabled) {
        if (!isEnabled) return@LaunchedEffect
        snapshotFlow { scrollableState.isScrollInProgress && scrollableState.lastScrolledForward }
            .distinctUntilChanged()
            .filter { it }
            .collect { keyboardController?.hide() }
    }
}
