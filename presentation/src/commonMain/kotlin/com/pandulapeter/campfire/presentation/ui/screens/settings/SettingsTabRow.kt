/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.settings

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.ui.components.controls.IndicatorSpan
import com.pandulapeter.campfire.presentation.ui.components.controls.pagerIndicatorSpan
import com.pandulapeter.campfire.presentation.ui.navigation.SettingsTab
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The tabs of the settings screen, which stay where they are while a page scrolls under them.
 *
 * The tabs start where every list of the app starts, each as wide as its name rather than an equal share of the row - five
 * tabs spread across a wide window are five words a hand's width apart, and five equal tabs at 360dp leave every name cut
 * short. The row scrolls all the way to the window's edges, under its insets, which only pad its first and last tab, so
 * the tabs it scrolls are neither cut off in the middle of the screen nor at a cutout.
 *
 * @param badgedTab A tab holding something that waits for an answer, marked with a dot so that it is found from the
 *   others. The dot rather than the tab opening itself, which would be the screen moving under a reader's finger.
 * @param indicatorPosition The pager's page plus its offset, which the indicator follows frame by frame - sliding and
 *   resizing from one tab's name to the next as the pages are swiped - rather than setting off on an animation of its
 *   own once the page being headed for changes. Read while laying the indicator out, so a swipe moves it without
 *   recomposing the row.
 * @param scrollState Where the row is scrolled, kept by the caller for the session: the row animates to the selected tab
 *   on its first layout unless it is already there, and a state made with the row would start at the first tab every time.
 * @param startPadding The window insets on the start edge, so the first tab starts after the cutout rather than under it.
 * @param endPadding The window insets on the end edge, so the last tab can be scrolled out from under the cutout.
 */
@Composable
internal fun SettingsTabRow(
    modifier: Modifier = Modifier,
    scrollState: ScrollState,
    tabs: List<SettingsTab>,
    selectedTab: SettingsTab,
    indicatorPosition: () -> Float,
    badgedTab: SettingsTab?,
    label: @Composable (SettingsTab) -> String,
    startPadding: Dp,
    endPadding: Dp,
    onTabSelected: (SettingsTab) -> Unit,
) = Column(modifier = modifier.fillMaxWidth().clipToBounds()) {
    PrimaryScrollableTabRow(
        modifier = Modifier.overflowNarrowerInset(startPadding = startPadding, endPadding = endPadding),
        selectedTabIndex = tabs.indexOf(selectedTab).coerceAtLeast(0),
        scrollState = scrollState,
        containerColor = Color.Transparent,
        edgePadding = maxOf(startPadding, endPadding),
        indicator = {
            TabRowDefaults.PrimaryIndicator(
                modifier = Modifier.tabIndicatorLayout { measurable, constraints, tabPositions ->
                    val span = pagerIndicatorSpan(
                        position = indicatorPosition(),
                        // Under the tab's name rather than across the whole tab, as Material's own indicator is.
                        spans = tabPositions.map { IndicatorSpan(start = (it.left + (it.width - it.contentWidth) / 2).toPx(), width = it.contentWidth.toPx()) },
                    )
                    val width = span?.width?.roundToInt() ?: 0
                    val placeable = measurable.measure(Constraints(minWidth = width, maxWidth = width, maxHeight = constraints.maxHeight))
                    // The row centers what this reports inside the selected tab on its own, so that centering is taken
                    // back off the span, which is measured from the start of the row. Both are logical, as the tab
                    // positions and the pager's position are, and placeRelative mirrors them in a right-to-left row.
                    val rowOffset = tabPositions.getOrNull(tabs.indexOf(selectedTab))?.let { ((it.width - it.contentWidth) / 2).toPx() } ?: 0f
                    layout(constraints.maxWidth, placeable.height) { placeable.placeRelative(((span?.start ?: 0f) - rowOffset).roundToInt(), 0) }
                },
                width = Dp.Unspecified,
            )
        },
        divider = {},
    ) {
        tabs.forEach { tab ->
            Tab(
                selected = tab == selectedTab,
                onClick = { onTabSelected(tab) },
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                text = {
                    BadgedBox(badge = { if (tab == badgedTab) Badge() }) {
                        Text(text = label(tab), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
            )
        }
    }
}

/**
 * The row pads both of its ends by one edge padding, the larger inset, so it is laid out wider than its parent by the
 * difference between the two insets, overflowing on the side of the smaller one: the part of that padding beyond what
 * the smaller inset asks for falls outside the parent, which clips it, and the last tab still scrolls up to that inset.
 */
private fun Modifier.overflowNarrowerInset(startPadding: Dp, endPadding: Dp) = layout { measurable, constraints ->
    val difference = (startPadding - endPadding).roundToPx()
    val overflow = if (constraints.hasBoundedWidth) abs(difference) else 0
    val placeable = measurable.measure(constraints.copy(minWidth = constraints.minWidth + overflow, maxWidth = constraints.maxWidth + overflow))
    // A larger end inset overflows on the start side, so the row is shifted back by the overflow; placeRelative mirrors
    // both cases for a right-to-left layout.
    layout(placeable.width - overflow, placeable.height) { placeable.placeRelative(if (difference < 0) -overflow else 0, 0) }
}
