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

import com.pandulapeter.campfire.presentation.ui.CampfireScreens
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailDefaults
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailDefaults
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.material3.WideNavigationRailItemDefaults
import androidx.compose.material3.WideNavigationRailValue
import androidx.compose.material3.rememberWideNavigationRailState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_tools
import com.pandulapeter.campfire.presentation.ui.components.ShrinkToFit
import com.pandulapeter.campfire.presentation.ui.metronome.MetronomeIconBeat
import com.pandulapeter.campfire.presentation.ui.metronome.rememberMetronomeIconBeat
import com.pandulapeter.campfire.presentation.resources.tools
import com.pandulapeter.campfire.presentation.ui.chrome.NavigationItemPresence
import com.pandulapeter.campfire.presentation.ui.chrome.collapsingNavigationItem
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import org.jetbrains.compose.resources.painterResource

/** Material adds the icon and its padding to this reserved label width, giving every item a 180dp pill. */
private val EXPANDED_NAVIGATION_RAIL_LABEL_WIDTH = 116.dp

/**
 * The narrowest the collapsed rail gets: Material 3 Expressive's collapsed rail, where `NavigationRail` still starts at
 * the older 80dp and wraps its widest item, whose label alone sets that item's width.
 */
private val NAVIGATION_RAIL_MIN_WIDTH = 96.dp

/**
 * The room the collapsed rail keeps on either side of its widest label. Without it a long one ("Einstellungen") widens
 * the rail until it touches both of its edges, where the bar gives every label a fifth of the window. No more than
 * this, so that the longest labels the app has (German "Einstellungen" and Spanish "Herramientas", about 84dp at the
 * default text size) still fit [NAVIGATION_RAIL_MIN_WIDTH] with a few dp to spare for rounding, and switching the
 * language does not change the rail's width.
 */
private val NAVIGATION_RAIL_HORIZONTAL_PADDING = 4.dp

/** Material's `NavigationRail` spacing between its items, repeated for the column they are wrapped in here. */
private val NAVIGATION_RAIL_ITEM_SPACING = 4.dp

/** The weight of a navigation bar item that has all but left, since a weight of zero is refused. */
private const val MIN_NAVIGATION_ITEM_WEIGHT = 0.001f

/** How far a navigation label that does not fit its item is made smaller before it is cut short instead. */
private const val MIN_NAVIGATION_LABEL_SCALE = 0.75f

/**
 * The navigation bar (under 600dp), navigation rail or expanded navigation rail (see [navigationChromeKind]) that
 * every top level screen shares. It belongs to the bottom of the deck rather than to any one screen: it is laid out
 * once for the window and stays there while the tabs fade through in place next to it. A card dealt over the deck
 * moves the screen under it a little, and the chrome is part of that screen as far as the eye can tell, so for as long
 * as a card covers the deck or is being taken off it every top level screen draws a copy of it instead (see
 * [CampfireScreens]), which moves with the screen and which the card covers. Where the chrome has no room for every top
 * level screen, as on the bar or on a rail too short for every item, it offers the tools behind one Tools item
 * ([areToolsGrouped]), whose screens then carry a button group between them ([ToolsButtonGroup]).
 *
 * @param destinations The top level screens of the features switched on, see `PreferencesController.topLevelDestinations`.
 *   The item of one that is not is disabled while it shrinks away, so that it takes no tap and no focus and is not
 *   announced, and drawn in its unselected colors when disabled, since a disabled item is dimmed - in one frame on the
 *   wide rail - and nothing but the shrink should change on screen.
 * @param metronomeBeat Where the Metronome item's icon is in the beat, see [rememberMetronomeIconBeat]: one state for
 *   every copy of the chrome, so that a copy drawn under a card moves in step with the shared one.
 * @param railCapacity How many items each kind of rail holds at the reader's text size, see [railCapacityIn]: the
 *   same for every copy of the chrome and for the tools' button group, which would otherwise disagree on the grouping.
 * @param lastTool The tool the Tools item opens where none is on screen, see [toolsItemDestination].
 * @param isKeyboard Whether the player's instrument is a keyboard, which the Chords item's icon is drawn for.
 * @param onToolsItemSelected The Tools item, with the tool it opens, see `CampfireViewModel.selectToolsItem`.
 */
@Composable
internal fun NavigationChrome(
    kind: NavigationChromeKind,
    destinations: List<CampfireDestination.TopLevel>,
    railCapacity: RailCapacity,
    currentTopLevelDestination: CampfireDestination.TopLevel?,
    metronomeBeat: MetronomeIconBeat,
    lastTool: CampfireDestination.TopLevel,
    isKeyboard: Boolean,
    onDestinationSelected: (CampfireDestination.TopLevel) -> Unit,
    onToolsItemSelected: (CampfireDestination.TopLevel) -> Unit,
) {
    // Worked out for this chrome's own kind rather than handed in for the window's: the scaffold composes the chrome it
    // is leaving for the length of a handover, and that one keeps its own grouping while it fades.
    val items = navigationBarItems(destinations, areToolsGrouped(kind, destinations, railCapacity.of(kind)))
    val onItemClick: (NavigationBarItem) -> Unit = { item ->
        when (item) {
            is NavigationBarItem.Destination -> onDestinationSelected(item.destination)
            NavigationBarItem.Tools -> toolsItemDestination(currentTopLevelDestination, lastTool, destinations)?.let(onToolsItemSelected)
        }
    }
    // A tool shrinking into the Tools item is still switched on, so what disables an item in every kind below is
    // whether it is shown rather than whether its feature is.
    if (kind == NavigationChromeKind.EXPANDED_RAIL) {
        // The wide rail's own collapsed state is not used: its collapsed form is wider than the plain rail, and the
        // window width alone decides which of the two a window gets, the scaffold handing one over to the other. The
        // state is only ever the expanded one.
        WideNavigationRail(
            state = rememberWideNavigationRailState(initialValue = WideNavigationRailValue.Expanded),
            windowInsets = WideNavigationRailDefaults.windowInsets.withMinTopEdge,
            // Every kind of chrome wears the bar's container color, so that a handover from one kind to another
            // changes its shape and not its color.
            colors = WideNavigationRailDefaults.colors(containerColor = NavigationBarDefaults.containerColor),
            // The default leaves room above the items for a header this rail does not have, which would drop them
            // 40dp lower than the collapsed rail's as the window crosses from the one to the other.
            contentPadding = PaddingValues(top = EXPANDED_NAVIGATION_RAIL_TOP_PADDING),
        ) {
            items.forEach { (item, isShown) ->
                NavigationItemPresence(isShown = isShown) { presence ->
                    WideNavigationRailItem(
                        modifier = Modifier.collapsingNavigationItem(presence = presence, isHorizontal = false),
                        selected = item.isSelected(currentTopLevelDestination),
                        onClick = { onItemClick(item) },
                        enabled = isShown,
                        colors = WideNavigationRailItemDefaults.colors().let {
                            it.copy(disabledIconColor = it.unselectedIconColor, disabledTextColor = it.unselectedTextColor)
                        },
                        icon = { NavigationBarItemIcon(item = item, metronomeBeat = metronomeBeat, isKeyboard = isKeyboard) },
                        label = {
                            Text(
                                text = stringResource(item.label),
                                modifier = Modifier.width(EXPANDED_NAVIGATION_RAIL_LABEL_WIDTH),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        railExpanded = true,
                    )
                }
            }
        }
    } else if (kind == NavigationChromeKind.RAIL) {
        NavigationRail(
            containerColor = NavigationBarDefaults.containerColor,
            windowInsets = NavigationRailDefaults.windowInsets.withMinTopEdge,
        ) {
            // The rail wraps its items, so its width is padded here, around them, rather than given to it: the rail
            // still grows with a longer label or a larger text size instead of cutting the label short.
            Column(
                modifier = Modifier
                    .widthIn(min = NAVIGATION_RAIL_MIN_WIDTH)
                    .padding(horizontal = NAVIGATION_RAIL_HORIZONTAL_PADDING),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(NAVIGATION_RAIL_ITEM_SPACING),
            ) {
                items.forEach { (item, isShown) ->
                    NavigationItemPresence(isShown = isShown) { presence ->
                        NavigationRailItem(
                            modifier = Modifier.collapsingNavigationItem(presence = presence, isHorizontal = false),
                            selected = item.isSelected(currentTopLevelDestination),
                            onClick = { onItemClick(item) },
                            enabled = isShown,
                            colors = NavigationRailItemDefaults.colors().let {
                                it.copy(disabledIconColor = it.unselectedIconColor, disabledTextColor = it.unselectedTextColor)
                            },
                            icon = { NavigationBarItemIcon(item = item, metronomeBeat = metronomeBeat, isKeyboard = isKeyboard) },
                            label = { NavigationItemLabel(stringResource(item.label)) },
                        )
                    }
                }
            }
        }
    } else {
        NavigationBar {
            items.forEach { (item, isShown) ->
                NavigationItemPresence(isShown = isShown) { presence ->
                    NavigationBarItem(
                        // The bar hands its width out by weight, and the outermost weight is the one it reads, so this
                        // one rather than the item's own decides the slot. A weight cannot be zero.
                        modifier = Modifier
                            .weight(presence().coerceAtLeast(MIN_NAVIGATION_ITEM_WEIGHT))
                            .collapsingNavigationItem(presence = presence, isHorizontal = true),
                        selected = item.isSelected(currentTopLevelDestination),
                        onClick = { onItemClick(item) },
                        enabled = isShown,
                        colors = NavigationBarItemDefaults.colors().let {
                            it.copy(disabledIconColor = it.unselectedIconColor, disabledTextColor = it.unselectedTextColor)
                        },
                        icon = { NavigationBarItemIcon(item = item, metronomeBeat = metronomeBeat, isKeyboard = isKeyboard) },
                        label = { NavigationItemLabel(stringResource(item.label)) },
                    )
                }
            }
        }
    }
}

/** Whether [this] item is the one selected: its own screen, or for the Tools item any tool, while it is on top. */
private fun NavigationBarItem.isSelected(currentTopLevelDestination: CampfireDestination.TopLevel?) = when (this) {
    is NavigationBarItem.Destination -> destination == currentTopLevelDestination
    NavigationBarItem.Tools -> currentTopLevelDestination in CampfireDestination.TopLevel.tools
}

private val NavigationBarItem.label
    get() = when (this) {
        is NavigationBarItem.Destination -> destination.label
        NavigationBarItem.Tools -> Res.string.tools
    }

@Composable
private fun NavigationBarItemIcon(item: NavigationBarItem, metronomeBeat: MetronomeIconBeat, isKeyboard: Boolean) = when (item) {
    is NavigationBarItem.Destination -> DestinationIcon(destination = item.destination, metronomeBeat = metronomeBeat, isKeyboard = isKeyboard)
    NavigationBarItem.Tools -> Icon(painter = painterResource(Res.drawable.ic_tools), contentDescription = null)
}

/**
 * A navigation item's label, on one line however large the text: one that does not fit its item is made smaller
 * rather than broken, since a word broken between its letters - five items share a phone's width - reads as two, and
 * past the floor it is cut short.
 */
@Composable
private fun NavigationItemLabel(text: String) {
    val fontSize = LocalTextStyle.current.fontSize
    Text(
        text = text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        autoSize = ShrinkToFit(
            minFontSize = fontSize * MIN_NAVIGATION_LABEL_SCALE,
            // The style's own size, or the auto size would grow a short label to fill its item.
            maxFontSize = fontSize,
        ),
    )
}
