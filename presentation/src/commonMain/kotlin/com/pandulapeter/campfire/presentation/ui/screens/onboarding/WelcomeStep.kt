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
import com.pandulapeter.campfire.presentation.resources.ic_campfire
import com.pandulapeter.campfire.presentation.resources.onboarding_welcome_message
import com.pandulapeter.campfire.presentation.resources.onboarding_welcome_title
import com.pandulapeter.campfire.presentation.resources.settings_user_interface_theme
import com.pandulapeter.campfire.presentation.resources.settings_user_interface_theme_color
import com.pandulapeter.campfire.presentation.ui.components.SettingsSubsection
import com.pandulapeter.campfire.presentation.ui.components.ThemeColorChoice
import com.pandulapeter.campfire.presentation.ui.components.UiModeChoice
import org.jetbrains.compose.resources.painterResource

/**
 * The welcome, and the first thing a newcomer gets to make their own: the theme and its color, shown on a few lines of a
 * song rather than on swatches alone, so that each tap shows what the app will look like with it. The onboarding is drawn
 * in the app's theme, so the page itself changes with them too.
 */
@Composable
internal fun WelcomeStep(
    modifier: Modifier = Modifier,
    uiMode: UserPreferences.UiMode?,
    themeColor: UserPreferences.ThemeColor?,
    notation: UserPreferences.Notation?,
    isCompact: Boolean,
    onUiModeSelected: (UserPreferences.UiMode) -> Unit,
    onThemeColorSelected: (UserPreferences.ThemeColor) -> Unit,
) = OnboardingPage(
    modifier = modifier,
    icon = painterResource(Res.drawable.ic_campfire),
    title = stringResource(Res.string.onboarding_welcome_title),
    message = stringResource(Res.string.onboarding_welcome_message),
    isCompact = isCompact,
) {
    OnboardingSongPreview(notation = notation)
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
}
