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
    selectedTab: SettingsTab,
    badgedTab: SettingsTab?,
    label: @Composable (SettingsTab) -> String,
    onTabSelected: (SettingsTab) -> Unit,
) = Column(
    modifier = modifier.width(SETTINGS_CATEGORY_PANE_WIDTH).padding(horizontal = 12.dp, vertical = PAGE_TOP_PADDING),
    verticalArrangement = Arrangement.spacedBy(4.dp),
) {
    SettingsTab.entries.forEach { tab ->
        NavigationDrawerItem(
            label = { Text(text = label(tab), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            selected = tab == selectedTab,
            onClick = { onTabSelected(tab) },
            badge = if (tab == badgedTab) ({ Badge() }) else null,
        )
    }
}
