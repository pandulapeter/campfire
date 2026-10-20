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

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination

/** One item of the navigation bar or rail: a top level screen, or the tools behind one item (see [areToolsGrouped]). */
internal sealed interface NavigationBarItem {

    data class Destination(val destination: CampfireDestination.TopLevel) : NavigationBarItem

    data object Tools : NavigationBarItem
}

/**
 * The most items a navigation bar holds: Material's own limit, and the most a 360dp phone fits with each label whole at
 * its own size.
 */
internal const val MAX_NAVIGATION_BAR_ITEMS = 5

/**
 * Whether the chrome offers the tools ([CampfireDestination.TopLevel.tools]) behind one Tools item, which opens them
 * with a button group over their screens: on the bar, and on any rail whose items at the reader's text size would not
 * all fit it ([railCapacity], see [railCapacityIn]), and only while the [destinations] of the features switched on would
 * not all fit it. A rail that holds them lists every screen, so a window that grows past the bar or tall enough for
 * every item hands the tools back their own items.
 */
internal fun areToolsGrouped(
    kind: NavigationChromeKind,
    destinations: List<CampfireDestination.TopLevel>,
    railCapacity: Int,
) = (kind == NavigationChromeKind.BAR || destinations.size > railCapacity) &&
    destinations.size > MAX_NAVIGATION_BAR_ITEMS &&
    destinations.count { it in CampfireDestination.TopLevel.tools } > 1

/**
 * How many items each kind of rail holds in the window, both worked out together: the root does not know the chrome's
 * kind, and a handover between the two rails composes both at once, each taking its own ([of]). The bar ignores it.
 */
@Immutable
internal data class RailCapacity(
    val rail: Int,
    val expandedRail: Int,
) {
    fun of(kind: NavigationChromeKind) = when (kind) {
        NavigationChromeKind.BAR -> Int.MAX_VALUE
        NavigationChromeKind.RAIL -> rail
        NavigationChromeKind.EXPANDED_RAIL -> expandedRail
    }
}

/**
 * How many items each rail [railHeight] tall (the window less the rail's insets) holds whole, its labels' lines
 * [railLabelHeight] and [expandedRailLabelHeight] tall at the reader's text size: what decides whether the tools must
 * go behind one item. Neither rail scrolls, so an item past its end could not be reached at all, and one more item
 * than fits is the first count that groups.
 */
internal fun railCapacityIn(railHeight: Dp, railLabelHeight: Dp, expandedRailLabelHeight: Dp) = RailCapacity(
    rail = itemCapacity(
        height = railHeight - RAIL_VERTICAL_PADDING * 2,
        itemHeight = max(RAIL_ITEM_MIN_HEIGHT, RAIL_ITEM_BASE_HEIGHT + railLabelHeight),
        gap = RAIL_ITEM_GAP,
    ),
    expandedRail = itemCapacity(
        height = railHeight - EXPANDED_NAVIGATION_RAIL_TOP_PADDING,
        itemHeight = max(EXPANDED_RAIL_ITEM_MIN_HEIGHT, expandedRailLabelHeight + EXPANDED_RAIL_ITEM_INDICATOR_PADDING),
        gap = 0.dp,
    ),
)

private fun itemCapacity(height: Dp, itemHeight: Dp, gap: Dp) = ((height + gap) / (itemHeight + gap)).toInt().coerceAtLeast(0)

/**
 * The gap the expanded rail leaves above its first item, in place of the default that leaves room for a header it does
 * not have; the same as the collapsed rail's, so that the items stay put as the window crosses from the one to the other.
 * It leaves none below.
 */
internal val EXPANDED_NAVIGATION_RAIL_TOP_PADDING = 4.dp

/**
 * Material's `NavigationRailItem` heights: at least 56dp, else its icon, its indicator's paddings, and the gap above
 * the label, which is all of it but the label's line. The label's auto-sizing only narrows it, never shortens its line.
 */
private val RAIL_ITEM_MIN_HEIGHT = 56.dp
private val RAIL_ITEM_BASE_HEIGHT = 40.dp

/**
 * Material's expanded `WideNavigationRailItem` heights: its indicator, 56dp at the least, is the label's line beside
 * the icon padded by 16dp above and below. That rail puts no gap between its items.
 */
private val EXPANDED_RAIL_ITEM_MIN_HEIGHT = 56.dp
private val EXPANDED_RAIL_ITEM_INDICATOR_PADDING = 32.dp

/** Material's `NavigationRail` spacing: its items' column is padded by this above and below and spaced by it. */
private val RAIL_VERTICAL_PADDING = 4.dp
private val RAIL_ITEM_GAP = 4.dp

/**
 * Every item the navigation bar or rail may hold, in its order, each with whether it is shown: the Tools item stands where the
 * first tool does, and the tools themselves come after it, so that switching the grouping on shrinks them away into it
 * and switching it off grows them back out of it. An item that is not shown is composed only while it is leaving.
 */
internal fun navigationBarItems(destinations: List<CampfireDestination.TopLevel>, areToolsGrouped: Boolean): List<Pair<NavigationBarItem, Boolean>> =
    CampfireDestination.TopLevel.entries.flatMap { destination ->
        val item = NavigationBarItem.Destination(destination) to (destination in destinations && !(areToolsGrouped && destination in CampfireDestination.TopLevel.tools))
        if (destination == CampfireDestination.TopLevel.tools.first()) listOf(NavigationBarItem.Tools to areToolsGrouped, item) else listOf(item)
    }

/**
 * The tool the Tools item opens: the one on screen, so that pressing the item again scrolls it to its top like any
 * other item does, and otherwise the one that was on screen last, or the first one switched on where that one has
 * been switched off since.
 */
internal fun toolsItemDestination(
    current: CampfireDestination.TopLevel?,
    lastTool: CampfireDestination.TopLevel,
    destinations: List<CampfireDestination.TopLevel>,
): CampfireDestination.TopLevel? {
    val tools = CampfireDestination.TopLevel.tools.filter { it in destinations }
    return current?.takeIf { it in tools } ?: lastTool.takeIf { it in tools } ?: tools.firstOrNull()
}
