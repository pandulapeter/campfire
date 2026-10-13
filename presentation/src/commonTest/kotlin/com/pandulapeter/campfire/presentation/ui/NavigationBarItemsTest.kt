/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui

import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination.Chords
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination.Metronome
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination.Settings
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination.Setlists
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination.Songs
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination.Tuner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class NavigationBarItemsTest {

    private val all = CampfireDestination.TopLevel.entries

    private fun shownItems(
        destinations: List<CampfireDestination.TopLevel>,
        kind: NavigationChromeKind = NavigationChromeKind.BAR,
        isShortWindow: Boolean = false,
    ) = navigationBarItems(destinations, areToolsGrouped(kind, destinations, isShortWindow)).filter { it.second }.map { it.first }

    @Test
    fun `a bar with no room for every screen offers the tools behind one item`() {
        assertTrue(areToolsGrouped(NavigationChromeKind.BAR, all, isShortWindow = false))
        assertEquals(
            listOf(NavigationBarItem.Destination(Songs), NavigationBarItem.Destination(Setlists), NavigationBarItem.Tools, NavigationBarItem.Destination(Settings)),
            shownItems(all),
        )
    }

    @Test
    fun `a tall rail offers every screen`() {
        assertFalse(areToolsGrouped(NavigationChromeKind.RAIL, all, isShortWindow = false))
        assertFalse(areToolsGrouped(NavigationChromeKind.EXPANDED_RAIL, all, isShortWindow = false))
    }

    @Test
    fun `a rail in a short window groups the tools`() {
        assertTrue(areToolsGrouped(NavigationChromeKind.RAIL, all, isShortWindow = true))
        assertTrue(areToolsGrouped(NavigationChromeKind.EXPANDED_RAIL, all, isShortWindow = true))
        assertEquals(
            listOf(NavigationBarItem.Destination(Songs), NavigationBarItem.Destination(Setlists), NavigationBarItem.Tools, NavigationBarItem.Destination(Settings)),
            shownItems(all, NavigationChromeKind.RAIL, isShortWindow = true),
        )
    }

    @Test
    fun `a bar is grouped whatever its height`() {
        assertTrue(areToolsGrouped(NavigationChromeKind.BAR, all, isShortWindow = true))
        assertTrue(areToolsGrouped(NavigationChromeKind.BAR, all, isShortWindow = false))
    }

    @Test
    fun `a short rail with five screens lists them all`() {
        assertFalse(areToolsGrouped(NavigationChromeKind.RAIL, all - Setlists, isShortWindow = true))
    }

    @Test
    fun `a bar that fits every screen switched on offers each of them`() {
        val withoutSetlists = all - Setlists
        assertFalse(areToolsGrouped(NavigationChromeKind.BAR, withoutSetlists, isShortWindow = false))
        assertEquals(withoutSetlists.map { NavigationBarItem.Destination(it) }, shownItems(withoutSetlists))
    }

    @Test
    fun `every item keeps its place whether it is shown or not`() {
        val grouped = navigationBarItems(all, areToolsGrouped = true).map { it.first }
        val ungrouped = navigationBarItems(all - Tuner, areToolsGrouped = false).map { it.first }
        assertEquals(grouped, ungrouped)
        assertEquals(NavigationBarItem.Tools, grouped[2])
    }

    @Test
    fun `the tools item opens the tool on screen, then the last one, then the first one switched on`() {
        assertEquals(Tuner, toolsItemDestination(current = Tuner, lastTool = Chords, destinations = all))
        assertEquals(Chords, toolsItemDestination(current = Songs, lastTool = Chords, destinations = all))
        assertEquals(Metronome, toolsItemDestination(current = null, lastTool = Metronome, destinations = all))
        assertEquals(Tuner, toolsItemDestination(current = Settings, lastTool = Chords, destinations = all - Chords - Metronome))
    }
}
