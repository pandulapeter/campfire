/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.settings_user_interface_theme_dark
import com.pandulapeter.campfire.presentation.resources.settings_user_interface_theme_light
import com.pandulapeter.campfire.presentation.resources.settings_user_interface_theme_system_default

/** Light, dark or whatever the system is set to, as the settings screen and the welcome sheet both offer it. */
@Composable
internal fun UiModeChoice(
    modifier: Modifier = Modifier,
    selected: UserPreferences.UiMode?,
    shouldApplyPadding: Boolean = true,
    onSelected: (UserPreferences.UiMode) -> Unit,
) = SegmentedChoice(
    modifier = modifier,
    options = listOf(
        UserPreferences.UiMode.SYSTEM_DEFAULT to stringResource(Res.string.settings_user_interface_theme_system_default),
        UserPreferences.UiMode.LIGHT to stringResource(Res.string.settings_user_interface_theme_light),
        UserPreferences.UiMode.DARK to stringResource(Res.string.settings_user_interface_theme_dark),
    ),
    shouldApplyPadding = shouldApplyPadding,
    selected = selected,
    onSelected = onSelected,
)
