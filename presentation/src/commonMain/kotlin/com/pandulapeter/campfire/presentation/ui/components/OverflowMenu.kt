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

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * The menu behind every overflow button in the app, and the one place that knows whether one of them is open: a
 * [DropdownMenu] keeps that next to the button that opened it, inside the composition, where the desktop window's
 * key handler (`CampfireViewModel.handleKeyEvent`) cannot see it - and an Escape pressed with a menu up belongs to
 * the menu rather than to the back stack.
 *
 * @param button The button that opens the menu, handed the way to open it.
 * @param content The entries of the menu, handed the way to close it, which every entry has to call before the
 *   dialog or screen it opens appears.
 */
@Composable
internal fun OverflowMenu(
    button: @Composable (open: () -> Unit) -> Unit,
    content: @Composable (dismiss: () -> Unit) -> Unit,
) {
    var isExpanded by remember { mutableStateOf(false) }
    if (isExpanded) {
        // Counted for as long as the menu is up, and given back by whatever takes it away - a choice, a click
        // outside it, or the row it hangs from leaving the list.
        DisposableEffect(Unit) {
            openOverflowMenuCount++
            onDispose { openOverflowMenuCount-- }
        }
    }
    Box {
        button { isExpanded = true }
        DropdownMenu(
            expanded = isExpanded,
            onDismissRequest = { isExpanded = false },
        ) {
            content { isExpanded = false }
        }
    }
}

/** True while any [OverflowMenu] is open, for the platform shells that have to know that before they act on a key. */
internal val isAnyOverflowMenuOpen get() = openOverflowMenuCount > 0

private var openOverflowMenuCount = 0
