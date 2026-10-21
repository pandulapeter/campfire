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

/**
 * The tabs of the settings screen, in the order they are read in: read only mode and what the app looks like, then how
 * the chords and the songs are written and shown, then where the library is and where else it goes, then what of it is
 * shared as links, then which of the app's parts it has at all, and what the app is last. General is what the screen opens on, and read only mode is its
 * first row: that switch decides what the rest of the app is still allowed to do, and it is the only way back out of
 * the mode, so it must never be something to go looking for.
 */
internal enum class SettingsTab {
    GENERAL,
    PREFERENCES,
    LIBRARY,
    SHARING,
    FEATURES,
    ABOUT,
}
