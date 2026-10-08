/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.settings

/**
 * The tabs of the settings screen, in the order they are read in: what the app looks like, then which of its parts it
 * has at all, then what a song is read with, then where the library is and where else it goes, and what the app is
 * last. General is what the screen opens on. Features is right after it, because read only mode is its first row and
 * that switch decides what the rest of the app is still allowed to do - it is also the only way back out of the mode,
 * so it must never be something to go looking for.
 */
internal enum class SettingsTab {
    GENERAL,
    FEATURES,
    SONGS,
    LIBRARY,
    ABOUT,
}
