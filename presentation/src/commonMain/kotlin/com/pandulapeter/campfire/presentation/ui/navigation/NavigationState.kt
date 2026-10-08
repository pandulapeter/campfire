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
 * Everything that decides where the user is in the app, which is more than the back stack: the search of a list
 * screen is closed by the back gesture before the screen is left, and the tab of the settings screen is what that
 * screen opens on. It is what the web build puts into the browser's address bar, one history entry per step a back
 * gesture would take, and what it hands back to the view model when an address is opened or the browser's Forward
 * returns to an entry.
 *
 * The song details destinations carry the page they are on as their `initialIndex`, so that the state opens the pager
 * on the song that was being read rather than on the one it was opened with.
 */
internal data class NavigationState(
    val backStack: List<CampfireDestination>,
    val isSongsSearchOpen: Boolean,
    val isSetlistsSearchOpen: Boolean,
    val settingsTab: SettingsTab,
)

/**
 * This state with its back stack cut short at the first screen of a feature switched off (see
 * [CampfireDestination.isEnabled]), the way an address naming a setlist the library no longer holds is: an address
 * typed or bookmarked, a history entry gone back to or a state saved before the switch was flipped may each name one.
 * The setlists' search is closed with their screen. What is left may be the songs alone, which is where the app then is.
 */
internal fun NavigationState.withoutDisabledFeatures(areSetlistsEnabled: Boolean, isMetronomeEnabled: Boolean): NavigationState {
    val backStack = backStack.takeWhile {
        CampfireDestination.isEnabled(destination = it, areSetlistsEnabled = areSetlistsEnabled, isMetronomeEnabled = isMetronomeEnabled)
    }
    return copy(
        backStack = backStack,
        isSetlistsSearchOpen = isSetlistsSearchOpen && CampfireDestination.Setlists in backStack,
    )
}
