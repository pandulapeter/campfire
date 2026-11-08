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
 * The tabs of the settings screen, in the order they are read in: the Preferences' pages first (how the app looks and
 * read only mode, how a song is read, how chords are drawn and written, how a click is played), each the same component
 * the Preferences sheet shows under the same name, then where the library is and where else it goes, then what of it is
 * shared as links, then which of the app's parts it has at all, and what the app is last. General is what the screen
 * opens on ([HOME]), and read only mode is its first row: that switch decides what the rest of the app is still allowed
 * to do, and it is the only way back out of the mode, so it must never be something to go looking for.
 */
internal enum class SettingsTab {
    GENERAL,
    SONG,
    CHORDS,
    METRONOME,
    LIBRARY,
    SHARING,
    FEATURES,
    ABOUT,
    ;

    companion object {
        /** The tab the screen opens on, and the one a way back goes to from any other before it leaves the screen. */
        val HOME = GENERAL
    }
}
