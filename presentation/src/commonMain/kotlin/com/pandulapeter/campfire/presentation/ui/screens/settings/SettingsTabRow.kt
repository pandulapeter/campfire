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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The tabs of the settings screen, which stay where they are while a page scrolls under them.
 *
 * The tabs are capped at [SETTINGS_TAB_ROW_MAX_WIDTH] and start where every list of the app starts - five tabs spread across a
 * wide window are five words a hand's width apart. Each is as wide as its name rather than a fifth of the row, and the row
 * scrolls where they add up to more than a phone's width: five equal tabs at 360dp leave every name cut short.
 *
 * @param badgedTab A tab holding something that waits for an answer, marked with a dot so that it is found from the
 *   others. The dot rather than the tab opening itself, which would be the screen moving under a reader's finger.
 * @param startPadding The window insets on the start edge, so the tabs start after the cutout rather than under it.
 */
@Composable
internal fun SettingsTabRow(
    modifier: Modifier = Modifier,
    selectedTab: SettingsTab,
    badgedTab: SettingsTab?,
    label: @Composable (SettingsTab) -> String,
    startPadding: Dp,
    endPadding: Dp,
    onTabSelected: (SettingsTab) -> Unit,
) = Column(modifier = modifier.fillMaxWidth()) {
    PrimaryScrollableTabRow(
        modifier = Modifier
            .padding(start = startPadding, end = endPadding)
            .widthIn(max = SETTINGS_TAB_ROW_MAX_WIDTH),
        selectedTabIndex = selectedTab.ordinal,
        containerColor = Color.Transparent,
        edgePadding = 0.dp,
        divider = {},
    ) {
        SettingsTab.entries.forEach { tab ->
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
