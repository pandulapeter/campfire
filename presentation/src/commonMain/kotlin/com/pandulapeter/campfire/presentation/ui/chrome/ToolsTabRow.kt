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

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.ui.metronome.MetronomeIconBeat
import com.pandulapeter.campfire.presentation.ui.components.fadingHorizontalEdges
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.settingsPage.SETTINGS_TAB_ROW_MAX_WIDTH

/**
 * The tabs between the tools behind the Tools item of the navigation bar or of a short window's rail (see
 * [areToolsGrouped]), standing where the settings screen's tabs do and drawn the way they are (`SettingsTabRow`): no
 * divider, capped at the same width, starting where the lists start, each tab as wide as its name, and the row scrolling
 * where they add up to more than the window's width, fading at an end past which there are more, since four equal tabs
 * on a 360dp phone leave "Metronome" cut short. Each carries its tool's icon over its name, the one its navigation item
 * would have ([DestinationIcon]), since the chrome's own item shows only the wrench. Each tab is a top level screen of
 * its own, selected as its item would be, so a tab already open scrolls its screen to the top.
 *
 * The row is painted in the background color rather than left transparent: the tool screens fade through under it,
 * and a transparent row would show both of them at once for the length of the fade.
 */
@Composable
internal fun ToolsTabRow(
    modifier: Modifier = Modifier,
    tools: List<CampfireDestination.TopLevel>,
    selected: CampfireDestination.TopLevel?,
    metronomeBeat: MetronomeIconBeat,
    isKeyboard: Boolean,
    onSelected: (CampfireDestination.TopLevel) -> Unit,
) {
    val scrollState = rememberScrollState()
    PrimaryScrollableTabRow(
        modifier = modifier
            .widthIn(max = SETTINGS_TAB_ROW_MAX_WIDTH)
            .height(TOOLS_TAB_ROW_HEIGHT)
            .fadingHorizontalEdges(scrollState),
        scrollState = scrollState,
        selectedTabIndex = tools.indexOf(selected).coerceAtLeast(0),
        containerColor = MaterialTheme.colorScheme.background,
        edgePadding = 0.dp,
        divider = {},
    ) {
        tools.forEach { tool ->
            Tab(
                selected = tool == selected,
                onClick = { onSelected(tool) },
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                text = { Text(text = stringResource(tool.label), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                icon = { DestinationIcon(destination = tool, metronomeBeat = metronomeBeat, isKeyboard = isKeyboard) },
            )
        }
    }
}

/**
 * The height of [ToolsTabRow], that of a tab with an icon and text, held fixed so that the room each tool screen keeps for the row is
 * always exactly what the row takes, whatever the text size.
 */
internal val TOOLS_TAB_ROW_HEIGHT = 72.dp
