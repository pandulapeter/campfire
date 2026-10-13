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

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.screens.settings.SETTINGS_TAB_ROW_MAX_WIDTH

/**
 * The tabs between the tools behind the navigation bar's Tools item (see [areToolsGrouped]), standing where the
 * settings screen's tabs do and drawn the way they are: on the background, with no divider, capped at the same width.
 * Each tab is a top level screen of its own, selected as its item would be, so a tab already open scrolls its screen
 * to the top. The three names fit a 360dp phone side by side, so the tabs share the row rather than scroll.
 */
@Composable
internal fun ToolsTabRow(
    modifier: Modifier = Modifier,
    tools: List<CampfireDestination.TopLevel>,
    selected: CampfireDestination.TopLevel?,
    onSelected: (CampfireDestination.TopLevel) -> Unit,
) = PrimaryTabRow(
    modifier = modifier
        .widthIn(max = SETTINGS_TAB_ROW_MAX_WIDTH)
        .height(TOOLS_TAB_ROW_HEIGHT),
    selectedTabIndex = tools.indexOf(selected).coerceAtLeast(0),
    containerColor = Color.Transparent,
    divider = {},
) {
    tools.forEach { tool ->
        Tab(
            selected = tool == selected,
            onClick = { onSelected(tool) },
            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            text = { Text(text = stringResource(tool.label), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        )
    }
}

/**
 * The height of [ToolsTabRow], a text tab's own, held fixed so that the room each tool screen keeps for the row is
 * always exactly what the row takes, whatever the text size.
 */
internal val TOOLS_TAB_ROW_HEIGHT = 48.dp
