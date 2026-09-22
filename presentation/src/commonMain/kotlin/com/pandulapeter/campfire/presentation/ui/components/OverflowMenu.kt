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
import androidx.compose.runtime.Stable
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
 * @param state Whether the menu is open, hoisted only where something other than [button] opens it too.
 * @param button The button that opens the menu, handed the way to open it.
 * @param content The entries of the menu, handed the way to choose one: every entry acts through it, which closes the
 *   menu before the action and ignores a second choice made while the menu is on its way out.
 */
@Composable
internal fun OverflowMenu(
    state: OverflowMenuState = rememberOverflowMenuState(),
    button: @Composable (open: () -> Unit) -> Unit,
    content: @Composable (select: (action: () -> Unit) -> Unit) -> Unit,
) {
    if (state.isExpanded) {
        // Counted for as long as the menu is up, and given back by whatever takes it away - a choice, a click
        // outside it, or the row it hangs from leaving the list.
        DisposableEffect(Unit) {
            openOverflowMenuCount++
            onDispose { openOverflowMenuCount-- }
        }
    }
    Box {
        button(state::open)
        DropdownMenu(
            expanded = state.isExpanded,
            onDismissRequest = state::dismiss,
        ) {
            content(state::select)
        }
    }
}

/**
 * Whether an [OverflowMenu] is open. A song row on the songs screen holds its own, because a long press on the row
 * opens the very menu its overflow button does, hanging from that button, rather than showing the same entries in a
 * different way.
 */
@Stable
internal class OverflowMenuState {

    var isExpanded by mutableStateOf(false)
        private set

    fun open() {
        isExpanded = true
    }

    fun dismiss() {
        isExpanded = false
    }

    /**
     * Closes the menu and runs [action] - once per opening. The menu does not leave with the tap that chose an entry
     * but with the end of its exit animation, and until then every entry in it can still be tapped: a double tap on
     * "Export" would download the file twice. The state is written as the tap is handled, so the second tap of the
     * same gesture already reads it.
     */
    fun select(action: () -> Unit) {
        if (!isExpanded) return
        isExpanded = false
        action()
    }
}

@Composable
internal fun rememberOverflowMenuState() = remember { OverflowMenuState() }

/** True while any [OverflowMenu] is open, for the platform shells that have to know that before they act on a key. */
internal val isAnyOverflowMenuOpen get() = openOverflowMenuCount > 0

private var openOverflowMenuCount = 0
