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

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_setlists
import com.pandulapeter.campfire.presentation.resources.ic_settings
import com.pandulapeter.campfire.presentation.resources.metronome
import com.pandulapeter.campfire.presentation.ui.metronome.MetronomeIcon
import com.pandulapeter.campfire.presentation.ui.metronome.MetronomeIconBeat
import com.pandulapeter.campfire.presentation.ui.metronome.rememberMetronomeIconBeat
import com.pandulapeter.campfire.presentation.resources.ic_songs
import com.pandulapeter.campfire.presentation.resources.setlists
import com.pandulapeter.campfire.presentation.resources.settings
import com.pandulapeter.campfire.presentation.resources.songs
import com.pandulapeter.campfire.presentation.ui.components.NavigationItemPresence
import com.pandulapeter.campfire.presentation.ui.components.collapsingNavigationItem
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource

/** Material adds the icon and its padding to this reserved label width, giving every item a 180dp pill. */
private val EXPANDED_NAVIGATION_RAIL_LABEL_WIDTH = 116.dp

/** The weight of a navigation bar item that has all but left, since a weight of zero is refused. */
private const val MIN_NAVIGATION_ITEM_WEIGHT = 0.001f

/** The gap the collapsed [NavigationRail] leaves above its first item. */
private val EXPANDED_NAVIGATION_RAIL_TOP_PADDING = 4.dp

/**
 * The navigation bar (under 600dp), navigation rail or expanded navigation rail (see [navigationChromeKind]) that
 * every top level screen shares. It belongs to the bottom of the deck rather than to any one screen: it is laid out
 * once for the window and stays there while the tabs fade through in place next to it. A card dealt over the deck
 * moves the screen under it a little, and the chrome is part of that screen as far as the eye can tell, so for as long
 * as a card covers the deck or is being taken off it every top level screen draws a copy of it instead (see
 * [CampfireScreens]), which moves with the screen and which the card covers.
 *
 * @param destinations The top level screens of the features switched on, see `CampfireViewModel.topLevelDestinations`.
 *   The item of one that is not is disabled while it shrinks away, so that it takes no tap and no focus and is not
 *   announced, and drawn in its unselected colors when disabled, since a disabled item is dimmed - in one frame on the
 *   wide rail - and nothing but the shrink should change on screen.
 * @param metronomeBeat Where the Metronome item's icon is in the beat, see [rememberMetronomeIconBeat]: one state for
 *   every copy of the chrome, so that a copy drawn under a card moves in step with the shared one.
 */
@Composable
internal fun NavigationChrome(
    kind: NavigationChromeKind,
    destinations: List<CampfireDestination.TopLevel>,
    currentTopLevelDestination: CampfireDestination.TopLevel?,
    metronomeBeat: MetronomeIconBeat,
    onDestinationSelected: (CampfireDestination.TopLevel) -> Unit,
) {
    if (kind == NavigationChromeKind.EXPANDED_RAIL) {
        // The wide rail's own collapsed state is not used: its collapsed form is wider than the plain rail, and the
        // window width alone decides which of the two a window gets, the scaffold handing one over to the other. The
        // state is only ever the expanded one.
        WideNavigationRail(
            state = rememberWideNavigationRailState(initialValue = WideNavigationRailValue.Expanded),
            windowInsets = WideNavigationRailDefaults.windowInsets.withMinTopEdge,
            // The default leaves room above the items for a header this rail does not have, which would drop them
            // 40dp lower than the collapsed rail's as the window crosses from the one to the other.
            contentPadding = PaddingValues(top = EXPANDED_NAVIGATION_RAIL_TOP_PADDING),
        ) {
            CampfireDestination.TopLevel.entries.forEach { destination ->
                NavigationItemPresence(isShown = destination in destinations) { presence ->
                    WideNavigationRailItem(
                        modifier = Modifier.collapsingNavigationItem(presence = presence, isHorizontal = false),
                        selected = destination == currentTopLevelDestination,
                        onClick = { onDestinationSelected(destination) },
                        enabled = destination in destinations,
                        colors = WideNavigationRailItemDefaults.colors().let {
                            it.copy(disabledIconColor = it.unselectedIconColor, disabledTextColor = it.unselectedTextColor)
                        },
                        icon = { DestinationIcon(destination = destination, metronomeBeat = metronomeBeat) },
                        label = {
                            Text(
                                text = stringResource(destination.label),
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
        NavigationRail(windowInsets = NavigationRailDefaults.windowInsets.withMinTopEdge) {
            CampfireDestination.TopLevel.entries.forEach { destination ->
                NavigationItemPresence(isShown = destination in destinations) { presence ->
                    NavigationRailItem(
                        modifier = Modifier.collapsingNavigationItem(presence = presence, isHorizontal = false),
                        selected = destination == currentTopLevelDestination,
                        onClick = { onDestinationSelected(destination) },
                        enabled = destination in destinations,
                        colors = NavigationRailItemDefaults.colors().let {
                            it.copy(disabledIconColor = it.unselectedIconColor, disabledTextColor = it.unselectedTextColor)
                        },
                        icon = { DestinationIcon(destination = destination, metronomeBeat = metronomeBeat) },
                        label = { Text(stringResource(destination.label)) },
                    )
                }
            }
        }
    } else {
        NavigationBar {
            CampfireDestination.TopLevel.entries.forEach { destination ->
                NavigationItemPresence(isShown = destination in destinations) { presence ->
                    NavigationBarItem(
                        // The bar hands its width out by weight, and the outermost weight is the one it reads, so this
                        // one rather than the item's own decides the slot. A weight cannot be zero.
                        modifier = Modifier
                            .weight(presence().coerceAtLeast(MIN_NAVIGATION_ITEM_WEIGHT))
                            .collapsingNavigationItem(presence = presence, isHorizontal = true),
                        selected = destination == currentTopLevelDestination,
                        onClick = { onDestinationSelected(destination) },
                        enabled = destination in destinations,
                        colors = NavigationBarItemDefaults.colors().let {
                            it.copy(disabledIconColor = it.unselectedIconColor, disabledTextColor = it.unselectedTextColor)
                        },
                        icon = { DestinationIcon(destination = destination, metronomeBeat = metronomeBeat) },
                        label = { Text(stringResource(destination.label)) },
                    )
                }
            }
        }
    }
}

/** The icon of a navigation item, the Metronome's swinging and pulsing with the click. */
@Composable
private fun DestinationIcon(
    destination: CampfireDestination.TopLevel,
    metronomeBeat: MetronomeIconBeat,
) = when (destination) {
    CampfireDestination.Songs -> Icon(painter = painterResource(Res.drawable.ic_songs), contentDescription = null)
    CampfireDestination.Setlists -> Icon(painter = painterResource(Res.drawable.ic_setlists), contentDescription = null)
    CampfireDestination.Metronome -> MetronomeIcon(beat = metronomeBeat, contentDescription = null)
    CampfireDestination.Settings -> Icon(painter = painterResource(Res.drawable.ic_settings), contentDescription = null)
}

private val CampfireDestination.TopLevel.label: StringResource
    get() = when (this) {
        CampfireDestination.Songs -> Res.string.songs
        CampfireDestination.Setlists -> Res.string.setlists
        CampfireDestination.Metronome -> Res.string.metronome
        CampfireDestination.Settings -> Res.string.settings
    }
