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
 * Its bottom is the home screen the switches leave ([rootedAt]), so with the songs off an address naming a tool opens
 * that tool over the first one. A list's search is closed with its screen. What is left may be the home screen alone,
 * which is where the app then is.
 */
internal fun NavigationState.withoutDisabledFeatures(features: FeatureSwitches): NavigationState {
    val backStack = backStack.rootedAt(features)
    return copy(
        backStack = backStack,
        isSongsSearchOpen = isSongsSearchOpen && CampfireDestination.Songs in backStack,
        isSetlistsSearchOpen = isSetlistsSearchOpen && CampfireDestination.Setlists in backStack,
    )
}

/**
 * This back stack on the home screen the switches leave ([CampfireDestination.TopLevel.home]) in place of whatever its
 * bottom is, and cut short at the first screen above it of a feature switched off. The bottom of a stack is only ever
 * its home screen, which the switches move: under a tab it is out of sight, and swapping it changes nothing on screen
 * but where Back goes. A tab that is the new home screen is not stacked over itself.
 *
 * The old bottom is kept over the new one where it is still switched on and no tab is over it, since then it is the tab
 * the user is on: Settings with everything else off, where switching a screen back on must not take them to it.
 */
internal fun List<CampfireDestination>.rootedAt(features: FeatureSwitches): List<CampfireDestination> {
    val home = CampfireDestination.TopLevel.home(features)
    val above = drop(1).takeWhile { CampfireDestination.isEnabled(it, features) }
    val currentTab = firstOrNull()?.takeIf { root ->
        root != home && root is CampfireDestination.TopLevel && CampfireDestination.isEnabled(root, features) &&
            above.none { it is CampfireDestination.TopLevel }
    }
    val rest = listOfNotNull(currentTab) + above
    return listOf(home) + if (rest.firstOrNull() == home) rest.drop(1) else rest
}
