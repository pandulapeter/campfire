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
 * with a row of tabs over their screens: on the bar, on a rail in a short window ([isShortWindow], a phone on its side),
 * whose height does not hold six items, and on any rail whose items at the reader's text size would not all fit it
 * ([railCapacity], see [railItemCapacity]), and only while the [destinations] of the features switched on would not all
 * fit it. A rail that holds them lists every screen, so a window that grows past the bar or the short height hands the
 * tools back their own items.
 */
internal fun areToolsGrouped(
    kind: NavigationChromeKind,
    destinations: List<CampfireDestination.TopLevel>,
    isShortWindow: Boolean,
    railCapacity: Int,
) = (kind == NavigationChromeKind.BAR || isShortWindow || destinations.size > railCapacity) &&
    destinations.size > MAX_NAVIGATION_BAR_ITEMS &&
    destinations.count { it in CampfireDestination.TopLevel.tools } > 1

/**
 * How many items a navigation rail [railHeight] tall holds, its items [itemHeight] tall with Material's gap between
 * them and its padding above and below: what decides, with the reader's text size in [itemHeight], whether the
 * tools must go behind one item on a rail that is not short. The rail does not scroll, so an item past its end could
 * not be reached at all.
 */
internal fun railItemCapacity(railHeight: Dp, itemHeight: Dp): Int =
    ((railHeight - RAIL_VERTICAL_PADDING * 2 + RAIL_ITEM_GAP) / (itemHeight + RAIL_ITEM_GAP)).toInt().coerceAtLeast(0)

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
 * A rail item's height without its label's line: Material's indicator and paddings, 56dp in all at a 16dp label. The
 * label's line grows with the reader's text size; its auto-sizing can only shorten an item, so a count from these
 * groups at worst slightly early.
 */
internal val RAIL_ITEM_BASE_HEIGHT = 40.dp

/**
 * An expanded rail item's least height, and what its label beside the icon adds to its line. That rail puts no gap
 * between its items, which [railItemCapacity] counts anyway, so it groups up to six gaps early, below the height where
 * every item fits at the default text size.
 */
internal val EXPANDED_RAIL_ITEM_MIN_HEIGHT = 56.dp
internal val EXPANDED_RAIL_ITEM_LABEL_PADDING = 16.dp

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
