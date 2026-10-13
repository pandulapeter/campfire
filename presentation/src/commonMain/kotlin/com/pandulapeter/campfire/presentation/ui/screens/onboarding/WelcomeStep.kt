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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_campfire
import com.pandulapeter.campfire.presentation.resources.ic_language
import com.pandulapeter.campfire.presentation.resources.onboarding_language
import com.pandulapeter.campfire.presentation.resources.onboarding_welcome_message
import com.pandulapeter.campfire.presentation.resources.onboarding_welcome_title
import com.pandulapeter.campfire.presentation.resources.settings_user_interface_theme
import com.pandulapeter.campfire.presentation.resources.settings_user_interface_theme_color
import com.pandulapeter.campfire.presentation.ui.components.BackgroundWarmthSlider
import com.pandulapeter.campfire.presentation.ui.components.SettingsSubsection
import com.pandulapeter.campfire.presentation.ui.components.ThemeColorChoice
import com.pandulapeter.campfire.presentation.ui.components.UiModeChoice
import com.pandulapeter.campfire.presentation.ui.components.languageName
import org.jetbrains.compose.resources.painterResource

/**
 * The welcome, and the first thing a newcomer gets to make their own: the language, behind a small button under the
 * greeting that opens the list in a sheet, so that somebody greeted in the wrong one can change it before reading on;
 * then the theme, its color and how far it is turned towards sepia. The onboarding is drawn in the app's theme, so the
 * page itself shows what each tap changes.
 */
@Composable
internal fun WelcomeStep(
    modifier: Modifier = Modifier,
    language: UserPreferences.Language?,
    uiMode: UserPreferences.UiMode?,
    themeColor: UserPreferences.ThemeColor?,
    backgroundWarmth: Int,
    isCompact: Boolean,
    onLanguageClicked: () -> Unit,
    onUiModeSelected: (UserPreferences.UiMode) -> Unit,
    onThemeColorSelected: (UserPreferences.ThemeColor) -> Unit,
    onBackgroundWarmthChanged: (Int) -> Unit,
) = OnboardingPage(
    modifier = modifier,
    icon = painterResource(Res.drawable.ic_campfire),
    title = stringResource(Res.string.onboarding_welcome_title),
    message = stringResource(Res.string.onboarding_welcome_message),
    isCompact = isCompact,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        TextButton(
            contentPadding = ButtonDefaults.TextButtonWithIconContentPadding,
            onClick = onLanguageClicked,
        ) {
            Icon(
                modifier = Modifier.size(ButtonDefaults.IconSize),
                painter = painterResource(Res.drawable.ic_language),
                contentDescription = null,
            )
            Text(
                modifier = Modifier.padding(start = ButtonDefaults.IconSpacing),
                text = stringResource(Res.string.onboarding_language, languageName(language ?: UserPreferences.Language.SYSTEM_DEFAULT)),
            )
        }
    }
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
