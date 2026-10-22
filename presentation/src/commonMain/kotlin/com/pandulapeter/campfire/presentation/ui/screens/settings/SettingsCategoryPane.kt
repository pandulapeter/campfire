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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Badge
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.ui.chrome.TOOL_PAGE_PADDING
import com.pandulapeter.campfire.presentation.ui.navigation.SettingsTab
import com.pandulapeter.campfire.presentation.ui.settingsPage.MAX_COLUMN_WIDTH
import com.pandulapeter.campfire.presentation.ui.settingsPage.PAGE_TOP_PADDING

/**
 * The tabs of the settings screen as a list at the start of the screen, for a window wide enough to hold the selected
 * page next to it: the way a tab is chosen where a row of tabs would be four words spread across the window. It is
 * [SETTINGS_CATEGORY_PANE_WIDTH] wide, so that the page beside it can be told how much room it has left.
 *
 * @param badgedTab A tab holding something that waits for an answer, marked with a dot the way [SettingsTabRow] does.
 */
@Composable
internal fun SettingsCategoryPane(
    modifier: Modifier = Modifier,
    tabs: List<SettingsTab>,
    selectedTab: SettingsTab,
    badgedTab: SettingsTab?,
    label: @Composable (SettingsTab) -> String,
    onTabSelected: (SettingsTab) -> Unit,
) = Column(
    modifier = modifier.width(SETTINGS_CATEGORY_PANE_WIDTH).padding(start = TOOL_PAGE_PADDING, end = 12.dp, top = PAGE_TOP_PADDING, bottom = PAGE_TOP_PADDING),
    verticalArrangement = Arrangement.spacedBy(4.dp),
) {
    tabs.forEach { tab ->
        NavigationDrawerItem(
            label = { Text(text = label(tab), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            selected = tab == selectedTab,
            onClick = { onTabSelected(tab) },
            badge = if (tab == badgedTab) ({ Badge() }) else null,
        )
    }
}

/** Leaves a full [MAX_COLUMN_WIDTH] for a page as soon as the tab row reaches its width cap. */
internal val SETTINGS_CATEGORY_PANE_WIDTH = 180.dp
