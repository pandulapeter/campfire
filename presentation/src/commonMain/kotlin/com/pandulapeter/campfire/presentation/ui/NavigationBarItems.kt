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
 * with a row of tabs over their screens: on the bar, and on a rail in a short window ([isShortWindow], a phone on its
 * side), whose height does not hold six items, and only while the [destinations] of the features switched on would not
 * all fit it. A taller rail lists every screen, so a window that grows past the bar or the short height hands the tools
 * back their own items.
 */
internal fun areToolsGrouped(kind: NavigationChromeKind, destinations: List<CampfireDestination.TopLevel>, isShortWindow: Boolean) =
    (kind == NavigationChromeKind.BAR || isShortWindow) &&
        destinations.size > MAX_NAVIGATION_BAR_ITEMS &&
        destinations.count { it in CampfireDestination.TopLevel.tools } > 1

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
