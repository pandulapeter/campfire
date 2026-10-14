/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.onboarding_look_message
import com.pandulapeter.campfire.presentation.resources.onboarding_look_title
import com.pandulapeter.campfire.presentation.resources.settings_user_interface_theme
import com.pandulapeter.campfire.presentation.resources.settings_user_interface_theme_color
import com.pandulapeter.campfire.presentation.ui.components.BackgroundWarmthSlider
import com.pandulapeter.campfire.presentation.ui.settingsPage.SettingsSubsection
import com.pandulapeter.campfire.presentation.ui.components.ThemeColorChoice
import com.pandulapeter.campfire.presentation.ui.components.UiModeChoice

/**
 * "Pick a look", the first thing a newcomer gets to make their own: the theme, its color and how far it is turned towards
 * sepia, the settings screen's own controls. The onboarding is drawn in the app's theme, so the page itself, and the mark
 * above it, show what each tap changes.
 */
@Composable
internal fun LookStep(
    modifier: Modifier = Modifier,
    markModifier: Modifier,
    uiMode: UserPreferences.UiMode?,
    themeColor: UserPreferences.ThemeColor?,
    backgroundWarmth: Int,
    isCompact: Boolean,
    onUiModeSelected: (UserPreferences.UiMode) -> Unit,
    onThemeColorSelected: (UserPreferences.ThemeColor) -> Unit,
    onBackgroundWarmthChanged: (Int) -> Unit,
) = OnboardingPage(
    modifier = modifier,
    markModifier = markModifier,
    title = stringResource(Res.string.onboarding_look_title),
    message = stringResource(Res.string.onboarding_look_message),
    isCompact = isCompact,
) {
    SettingsSubsection(
        title = stringResource(Res.string.settings_user_interface_theme),
        shouldApplyPadding = false,
    ) {
        UiModeChoice(
            selected = uiMode,
            onSelected = onUiModeSelected,
        )
    }
    SettingsSubsection(
        title = stringResource(Res.string.settings_user_interface_theme_color),
        shouldApplyPadding = false,
    ) {
        ThemeColorChoice(
            uiMode = uiMode,
            selected = themeColor,
            onSelected = onThemeColorSelected,
        )
    }
    BackgroundWarmthSlider(
        warmth = backgroundWarmth,
        shouldApplyPadding = false,
        onWarmthChanged = onBackgroundWarmthChanged,
    )
}
