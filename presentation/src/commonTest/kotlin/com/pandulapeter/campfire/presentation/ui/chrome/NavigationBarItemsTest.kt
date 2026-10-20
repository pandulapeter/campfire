/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.chrome

import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination.Chords
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination.Metronome
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination.Scales
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
        railCapacity: Int = Int.MAX_VALUE,
    ) = navigationBarItems(destinations, areToolsGrouped(kind, destinations, railCapacity)).filter { it.second }.map { it.first }

    @Test
    fun `a bar with no room for every screen offers the tools behind one item`() {
        assertTrue(areToolsGrouped(NavigationChromeKind.BAR, all, railCapacity = Int.MAX_VALUE))
        assertEquals(
            listOf(NavigationBarItem.Destination(Songs), NavigationBarItem.Destination(Setlists), NavigationBarItem.Tools, NavigationBarItem.Destination(Settings)),
            shownItems(all),
        )
    }

    @Test
    fun `a tall rail offers every screen`() {
        assertFalse(areToolsGrouped(NavigationChromeKind.RAIL, all, railCapacity = Int.MAX_VALUE))
        assertFalse(areToolsGrouped(NavigationChromeKind.EXPANDED_RAIL, all, railCapacity = Int.MAX_VALUE))
    }

    @Test
    fun `a rail too short for every screen groups the tools`() {
        assertTrue(areToolsGrouped(NavigationChromeKind.RAIL, all, railCapacity = 6))
        assertTrue(areToolsGrouped(NavigationChromeKind.EXPANDED_RAIL, all, railCapacity = 6))
        assertEquals(
            listOf(NavigationBarItem.Destination(Songs), NavigationBarItem.Destination(Setlists), NavigationBarItem.Tools, NavigationBarItem.Destination(Settings)),
            shownItems(all, NavigationChromeKind.RAIL, railCapacity = 6),
        )
    }

    @Test
    fun `a rail that holds every screen lists them all`() {
        assertFalse(areToolsGrouped(NavigationChromeKind.RAIL, all, railCapacity = 7))
    }

    @Test
    fun `a rail holds every item that fits whole, with the gaps between them`() {
        assertEquals(7, railCapacityIn(424.dp, railLabelHeight = 16.dp, expandedRailLabelHeight = 20.dp).rail)
        assertEquals(6, railCapacityIn(423.dp, railLabelHeight = 16.dp, expandedRailLabelHeight = 20.dp).rail)
        assertEquals(7, railCapacityIn(536.dp, railLabelHeight = 32.dp, expandedRailLabelHeight = 20.dp).rail)
        assertEquals(6, railCapacityIn(535.dp, railLabelHeight = 32.dp, expandedRailLabelHeight = 20.dp).rail)
    }

    @Test
    fun `a rail's items keep their least height under a small text size`() {
        assertEquals(6, railCapacityIn(423.dp, railLabelHeight = 12.dp, expandedRailLabelHeight = 15.dp).rail)
        assertEquals(6, railCapacityIn(395.dp, railLabelHeight = 12.dp, expandedRailLabelHeight = 15.dp).expandedRail)
    }

    @Test
    fun `an expanded rail holds every item that fits whole, with no gaps between them`() {
        assertEquals(7, railCapacityIn(396.dp, railLabelHeight = 16.dp, expandedRailLabelHeight = 20.dp).expandedRail)
        assertEquals(6, railCapacityIn(395.dp, railLabelHeight = 16.dp, expandedRailLabelHeight = 20.dp).expandedRail)
        assertEquals(7, railCapacityIn(508.dp, railLabelHeight = 16.dp, expandedRailLabelHeight = 40.dp).expandedRail)
        assertEquals(6, railCapacityIn(507.dp, railLabelHeight = 16.dp, expandedRailLabelHeight = 40.dp).expandedRail)
    }

    @Test
    fun `a bar is grouped whatever its height`() {
        assertTrue(areToolsGrouped(NavigationChromeKind.BAR, all, railCapacity = Int.MAX_VALUE))
    }

    @Test
    fun `five screens are never grouped, however short the rail`() {
        assertFalse(areToolsGrouped(NavigationChromeKind.RAIL, all - Setlists - Scales, railCapacity = 4))
    }

    @Test
    fun `a bar that fits every screen switched on offers each of them`() {
        val fitting = all - Setlists - Scales
        assertFalse(areToolsGrouped(NavigationChromeKind.BAR, fitting, railCapacity = Int.MAX_VALUE))
        assertEquals(fitting.map { NavigationBarItem.Destination(it) }, shownItems(fitting))
    }

    @Test
    fun `the four tools are grouped behind one item, which never holds a tool alone`() {
        assertEquals(listOf(Metronome, Tuner, Chords, Scales), CampfireDestination.TopLevel.tools)
        assertTrue(areToolsGrouped(NavigationChromeKind.BAR, all - Setlists, railCapacity = Int.MAX_VALUE))
        assertFalse(areToolsGrouped(NavigationChromeKind.BAR, listOf(Songs, Setlists, Scales, Settings), railCapacity = Int.MAX_VALUE))
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
