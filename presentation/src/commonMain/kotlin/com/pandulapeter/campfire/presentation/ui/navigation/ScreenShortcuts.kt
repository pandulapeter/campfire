/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.navigation

import com.pandulapeter.campfire.presentation.ui.components.shortcuts.KeyShortcut

/**
 * The key that opens [destination] of the main screens switched on, [destinations]: they are numbered in the chrome's
 * order, so that Ctrl / Cmd + 1 is always the first item and Settings the last. Seven numbers are every screen; a tool
 * behind the Tools item keeps its number, which its button in the group names.
 */
internal fun screenShortcutOf(destination: CampfireDestination.TopLevel, destinations: List<CampfireDestination.TopLevel>): KeyShortcut? =
    KeyShortcut.screens.getOrNull(destinations.indexOf(destination))

/** The main screen [shortcut] opens, the other way round from [screenShortcutOf], or null where it opens none. */
internal fun screenOf(shortcut: KeyShortcut, destinations: List<CampfireDestination.TopLevel>): CampfireDestination.TopLevel? =
    destinations.firstOrNull { screenShortcutOf(it, destinations) == shortcut }
