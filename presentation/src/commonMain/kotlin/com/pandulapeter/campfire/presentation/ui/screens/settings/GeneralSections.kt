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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.Composable
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_campfire
import com.pandulapeter.campfire.presentation.resources.settings_app_icon_browser_tab
import com.pandulapeter.campfire.presentation.resources.settings_app_icon_browser_tab_description
import com.pandulapeter.campfire.presentation.resources.settings_app_icon_home_screen
import com.pandulapeter.campfire.presentation.resources.settings_app_icon_home_screen_description
import com.pandulapeter.campfire.presentation.resources.settings_app_icon_launcher
import com.pandulapeter.campfire.presentation.resources.settings_app_icon_launcher_description
import com.pandulapeter.campfire.presentation.resources.settings_background_warmth_description
import com.pandulapeter.campfire.presentation.resources.settings_group_appearance
import com.pandulapeter.campfire.presentation.resources.settings_group_performance
import com.pandulapeter.campfire.presentation.resources.settings_group_setup
import com.pandulapeter.campfire.presentation.resources.settings_onboarding
import com.pandulapeter.campfire.presentation.resources.settings_onboarding_description
import com.pandulapeter.campfire.presentation.resources.settings_read_only_mode
import com.pandulapeter.campfire.presentation.resources.settings_read_only_mode_description
import com.pandulapeter.campfire.presentation.resources.settings_read_only_mode_description_without_setlists
import com.pandulapeter.campfire.presentation.resources.settings_app_icon_window
import com.pandulapeter.campfire.presentation.resources.settings_app_icon_window_description
import com.pandulapeter.campfire.presentation.resources.settings_user_interface_language
import com.pandulapeter.campfire.presentation.resources.settings_user_interface_theme
import com.pandulapeter.campfire.presentation.resources.settings_user_interface_theme_color
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.preferences.BackgroundWarmthSlider
import com.pandulapeter.campfire.presentation.ui.components.preferences.LanguageChoice
import com.pandulapeter.campfire.presentation.ui.settingsPage.SettingsSubsection
import com.pandulapeter.campfire.presentation.ui.components.SwitchListItem
import com.pandulapeter.campfire.presentation.ui.components.preferences.ThemeColorChoice
import com.pandulapeter.campfire.presentation.ui.components.preferences.UiModeChoice
import com.pandulapeter.campfire.presentation.ui.platform.AppIconSurface
import com.pandulapeter.campfire.presentation.ui.platform.appIconSurface
import com.pandulapeter.campfire.presentation.ui.settingsPage.SettingsSection
import com.pandulapeter.campfire.presentation.ui.theme.CampfireColorScheme
import com.pandulapeter.campfire.presentation.ui.theme.colorSchemePair
import org.jetbrains.compose.resources.painterResource

/**
 * The General tab: read only mode, then the way back into the first run's steps, then how the app looks, then which
 * language it speaks. Read only mode leads, since General is the tab Settings opens on: it decides what the rest of the
 * app is still allowed to do and is the only way back out of the mode, so it must never be something to go looking for.
 * It is only there while the songs are, since everything it locks is about them. Setting the app up again comes next,
 * although it walks through every tab's first questions rather than this one's.
 */
@Composable
internal fun GeneralSections(
    viewModel: CampfireViewModel,
    userPreferences: UserPreferences?,
    isPerformanceModeEnabled: Boolean,
) {
    AnimatedVisibility(
        visible = userPreferences?.areSongsEnabled != false,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        PerformanceSection(
            isPerformanceModeEnabled = isPerformanceModeEnabled,
            areSetlistsEnabled = userPreferences?.areSetlistsEnabled != false,
            onPerformanceModeChanged = viewModel.preferencesController::setPerformanceModeEnabled,
        )
    }
    SetupSection(onOnboardingClicked = viewModel::openOnboarding)
    AppearanceSection(viewModel = viewModel, userPreferences = userPreferences)
    LanguageSection(viewModel = viewModel, userPreferences = userPreferences)
}

@Composable
private fun PerformanceSection(
    isPerformanceModeEnabled: Boolean,
    areSetlistsEnabled: Boolean,
    onPerformanceModeChanged: (Boolean) -> Unit,
) = SettingsSection(title = stringResource(Res.string.settings_group_performance)) {
    SwitchListItem(
        title = stringResource(Res.string.settings_read_only_mode),
        // Reordering is a setlist's, so with the setlists off there is none to lock.
        description = stringResource(
            if (areSetlistsEnabled) Res.string.settings_read_only_mode_description else Res.string.settings_read_only_mode_description_without_setlists,
        ),
        isChecked = isPerformanceModeEnabled,
        onCheckedChange = onPerformanceModeChanged,
    )
}

/** The steps the first run opens with, which start from what everything is set to now. */
@Composable
private fun SetupSection(
    onOnboardingClicked: () -> Unit,
) = SettingsSection(title = stringResource(Res.string.settings_group_setup)) {
    LinkListItem(
        title = stringResource(Res.string.settings_onboarding),
        description = stringResource(Res.string.settings_onboarding_description),
        icon = painterResource(Res.drawable.ic_campfire),
        onClick = onOnboardingClicked,
    )
}

@Composable
private fun AppearanceSection(
    viewModel: CampfireViewModel,
    userPreferences: UserPreferences?,
) = SettingsSection(title = stringResource(Res.string.settings_group_appearance)) {
    SettingsSubsection(title = stringResource(Res.string.settings_user_interface_theme)) {
        UiModeChoice(
            selected = userPreferences?.uiMode,
            onSelected = viewModel.preferencesController::setUiMode,
        )
    }
    SettingsSubsection(title = stringResource(Res.string.settings_user_interface_theme_color)) {
        ThemeColorChoice(
            uiMode = userPreferences?.uiMode,
            selected = userPreferences?.themeColor,
            onSelected = viewModel.preferencesController::setThemeColor,
        )
    }
    // Named after the icon it colors, which is a different one on every platform, and described with what that
    // platform lets it do: the one thing all of them share is that a color picked here reaches the icon at all. It is
    // disabled wherever the theme is drawn in the app's own palette - that color itself, or the System color on a
    // device that hands out none - since the icon is the app's own then whichever way it is set. The System color where
    // it is honored is a color like the rest, whose launcher icon the wallpaper tints. Disabled, it shows as on, since
    // the app's own icon is then the icon of the color picked rather than the icon kept instead of it; the preference
    // itself is left as the user set it, for the next color that can reach the icon.
    val canColorAppIcon = colorSchemePair(userPreferences?.themeColor) !== CampfireColorScheme
    appIconSurface?.let { surface ->
        SwitchListItem(
            title = surface.title(),
            description = surface.description(),
            isChecked = !canColorAppIcon || userPreferences?.isAppIconThemed == true,
            isEnabled = canColorAppIcon,
            onCheckedChange = viewModel.preferencesController::setAppIconThemed,
        )
    }
    BackgroundWarmthSlider(
        warmth = userPreferences?.backgroundWarmth ?: 0,
        description = stringResource(Res.string.settings_background_warmth_description),
        onWarmthChanged = viewModel.preferencesController::setBackgroundWarmth,
    )
}

@Composable
private fun LanguageSection(
    viewModel: CampfireViewModel,
    userPreferences: UserPreferences?,
) = SettingsSection(title = stringResource(Res.string.settings_user_interface_language)) {
    LanguageChoice(
        selected = userPreferences?.language,
        onSelected = viewModel.preferencesController::setLanguage,
    )
}

/** What the icon the theme color can reach is called on this platform. */
@Composable
private fun AppIconSurface.title() = stringResource(
    when (this) {
        AppIconSurface.LAUNCHER -> Res.string.settings_app_icon_launcher
        AppIconSurface.HOME_SCREEN -> Res.string.settings_app_icon_home_screen
        AppIconSurface.WINDOW -> Res.string.settings_app_icon_window
        AppIconSurface.BROWSER_TAB -> Res.string.settings_app_icon_browser_tab
    }
)

/** What coloring that icon does here, and where it stops. */
@Composable
private fun AppIconSurface.description() = stringResource(
    when (this) {
        AppIconSurface.LAUNCHER -> Res.string.settings_app_icon_launcher_description
        AppIconSurface.HOME_SCREEN -> Res.string.settings_app_icon_home_screen_description
        AppIconSurface.WINDOW -> Res.string.settings_app_icon_window_description
        AppIconSurface.BROWSER_TAB -> Res.string.settings_app_icon_browser_tab_description
    }
)
