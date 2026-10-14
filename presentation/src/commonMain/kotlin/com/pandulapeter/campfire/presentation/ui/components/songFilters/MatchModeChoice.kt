/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components.songFilters

import com.pandulapeter.campfire.presentation.ui.components.controls.CHIP_GAP
import com.pandulapeter.campfire.presentation.ui.components.controls.CONTROLS_PADDING
import com.pandulapeter.campfire.presentation.ui.components.controls.SegmentedChoice
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.presentation.ui.settingsPage.SettingsSectionTitle

/**
 * Whether the values selected in a filter group mean "any of them" or "all of them", under the group's chips. Only
 * worth asking about once two of them are on, since one value means the same thing either way.
 */
@Composable
internal fun MatchModeChoice(
    modifier: Modifier = Modifier,
    isVisible: Boolean,
    title: String,
    anyText: String,
    allText: String,
    matchMode: UserPreferences.MatchMode,
    onMatchModeSelected: (UserPreferences.MatchMode) -> Unit,
) = AnimatedVisibility(
    modifier = modifier,
    visible = isVisible,
    enter = expandVertically() + fadeIn(),
    exit = shrinkVertically() + fadeOut(),
) {
    Column {
        SettingsSectionTitle(
            text = title,
            contentPadding = PaddingValues(horizontal = CONTROLS_PADDING, vertical = CHIP_GAP),
        )
        SegmentedChoice(
            options = listOf(
                UserPreferences.MatchMode.ANY to anyText,
                UserPreferences.MatchMode.ALL to allText,
            ),
            selected = matchMode,
            onSelected = onMatchModeSelected,
        )
    }
}
