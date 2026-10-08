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

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Whether any [OverflowMenu] is open, for the platform shells that have to know that before they act on a key, and for
 * the web build's history, which gives an open menu an entry of its own (which is why it is a snapshot state). A
 * [androidx.compose.material3.DropdownMenu] keeps that next to the button that opened it, inside the composition, where
 * the desktop window's key handler cannot see it, so every menu counts itself in here for as long as it is up.
 *
 * The view model owns the one the app reads and [LocalOverlayState] hands it to the menus, so that nothing about it is
 * shared between two compositions of the app in one process - the screenshot tool's renders, or a test.
 */
@Stable
internal class OverlayState {

    private var openMenuCount by mutableIntStateOf(0)

    val isAnyMenuOpen: Boolean get() = openMenuCount > 0

    fun onMenuOpened() {
        openMenuCount++
    }

    fun onMenuClosed() {
        openMenuCount--
    }
}

/**
 * The [OverlayState] the menus count themselves into, provided by `CampfireApp`. The default keeps a menu composed
 * outside the app working.
 */
internal val LocalOverlayState = staticCompositionLocalOf { OverlayState() }
