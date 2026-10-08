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
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.localization.AppLocale
import com.pandulapeter.campfire.presentation.localization.LocalizedStrings
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.settings_app_icon_browser_tab
import com.pandulapeter.campfire.presentation.resources.settings_app_icon_browser_tab_description
import com.pandulapeter.campfire.presentation.resources.settings_app_icon_dock
import com.pandulapeter.campfire.presentation.resources.settings_app_icon_dock_description
import com.pandulapeter.campfire.presentation.resources.settings_app_icon_home_screen
import com.pandulapeter.campfire.presentation.resources.settings_app_icon_home_screen_description
import com.pandulapeter.campfire.presentation.resources.settings_app_icon_launcher
import com.pandulapeter.campfire.presentation.resources.settings_app_icon_launcher_description
import com.pandulapeter.campfire.presentation.resources.settings_app_icon_taskbar
import com.pandulapeter.campfire.presentation.resources.settings_app_icon_taskbar_description
import com.pandulapeter.campfire.presentation.resources.settings_background_warmth
import com.pandulapeter.campfire.presentation.resources.settings_background_warmth_description
import com.pandulapeter.campfire.presentation.resources.settings_app_icon_window
import com.pandulapeter.campfire.presentation.resources.settings_app_icon_window_description
import com.pandulapeter.campfire.presentation.resources.settings_user_interface_language
import com.pandulapeter.campfire.presentation.resources.settings_user_interface_language_english
import com.pandulapeter.campfire.presentation.resources.settings_user_interface_language_hungarian
import com.pandulapeter.campfire.presentation.resources.settings_user_interface_language_more_coming
import com.pandulapeter.campfire.presentation.resources.settings_user_interface_language_system_default
import com.pandulapeter.campfire.presentation.resources.settings_user_interface_language_with_own_name
import com.pandulapeter.campfire.presentation.resources.settings_user_interface_theme
import com.pandulapeter.campfire.presentation.resources.settings_user_interface_theme_color
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.RadioListItem
import com.pandulapeter.campfire.presentation.ui.components.SettingsSubsection
import com.pandulapeter.campfire.presentation.ui.components.SwitchListItem
import com.pandulapeter.campfire.presentation.ui.components.ThemeColorChoice
import com.pandulapeter.campfire.presentation.ui.components.UiModeChoice
import com.pandulapeter.campfire.presentation.ui.platform.AppIconSurface
import com.pandulapeter.campfire.presentation.ui.platform.appIconSurface
import com.pandulapeter.campfire.presentation.ui.theme.CampfireColorScheme
import com.pandulapeter.campfire.presentation.ui.theme.colorSchemePair
import kotlin.math.roundToInt

/**
 * How far the palette's neutrals are turned towards sepia. A slider of a few steps rather than a picker of tints, since
 * every step keeps every contrast ratio of the palette (see `withBackgroundWarmth`), so no step can cost legibility,
 * and a step is a whole theme change that recomposes the app: a continuous slider would do that on every frame of a
 * drag. Each step reaches the theme as it is crossed, so the app behind the settings shows where the thumb is.
 */
@Composable
private fun BackgroundWarmthSlider(
    warmth: Int,
    onWarmthChanged: (Int) -> Unit,
) = SettingsSubsection(
    title = stringResource(Res.string.settings_background_warmth),
    description = stringResource(Res.string.settings_background_warmth_description),
) {
    val sliderDescription = stringResource(Res.string.settings_background_warmth)
    Slider(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).semantics { contentDescription = sliderDescription },
        value = warmth.toFloat(),
        onValueChange = { value -> value.roundToInt().let { if (it != warmth) onWarmthChanged(it) } },
        valueRange = 0f..UserPreferences.MAX_BACKGROUND_WARMTH.toFloat(),
        steps = UserPreferences.MAX_BACKGROUND_WARMTH - 1,
    )
}

@Composable
internal fun GeneralSection(
    viewModel: CampfireViewModel,
    userPreferences: UserPreferences?,
) = SettingsSection {
    SettingsSubsection(title = stringResource(Res.string.settings_user_interface_theme)) {
        UiModeChoice(
            selected = userPreferences?.uiMode,
            onSelected = viewModel::setUiMode,
        )
    }
    SettingsSubsection(title = stringResource(Res.string.settings_user_interface_theme_color)) {
        ThemeColorChoice(
            uiMode = userPreferences?.uiMode,
            selected = userPreferences?.themeColor,
            onSelected = viewModel::setThemeColor,
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
    SwitchListItem(
        title = appIconSurface.title(),
        description = appIconSurface.description(),
        isChecked = !canColorAppIcon || userPreferences?.isAppIconThemed == true,
        isEnabled = canColorAppIcon,
        onCheckedChange = viewModel::setAppIconThemed,
    )
    BackgroundWarmthSlider(
        warmth = userPreferences?.backgroundWarmth ?: 0,
        onWarmthChanged = viewModel::setBackgroundWarmth,
    )
    // A list rather than a segmented control, since it is the one choice here that grows with every translation,
    // and a row of segments runs out of width after the third. Every language the app is not set to also carries its
    // name in itself, read out of its own string table, so that somebody who ended up in one they cannot read can
    // still find theirs; the one it is set to is already named in itself, and saying so twice reads as a mistake.
    SettingsSubsection(title = stringResource(Res.string.settings_user_interface_language)) {
        Column(modifier = Modifier.selectableGroup()) {
            listOf(
                UserPreferences.Language.SYSTEM_DEFAULT to Res.string.settings_user_interface_language_system_default,
                UserPreferences.Language.ENGLISH to Res.string.settings_user_interface_language_english,
                UserPreferences.Language.HUNGARIAN to Res.string.settings_user_interface_language_hungarian,
            ).forEach { (language, name) ->
                val isSelected = userPreferences?.language == language
                val localName = stringResource(name)
                val ownName = if (language == UserPreferences.Language.SYSTEM_DEFAULT) {
                    localName
                } else {
                    LocalizedStrings.get(name, locale = AppLocale.findByCode(language.id))
                }
                RadioListItem(
                    title = if (isSelected || ownName == localName) {
                        localName
                    } else {
                        stringResource(Res.string.settings_user_interface_language_with_own_name, localName, ownName)
                    },
                    isSelected = isSelected,
                    onSelected = { viewModel.setLanguage(language) },
                )
            }
        }
        SettingsMessage(text = stringResource(Res.string.settings_user_interface_language_more_coming))
    }
}

/** What the icon the theme color can reach is called on this platform. */
@Composable
private fun AppIconSurface.title() = stringResource(
    when (this) {
        AppIconSurface.LAUNCHER -> Res.string.settings_app_icon_launcher
        AppIconSurface.HOME_SCREEN -> Res.string.settings_app_icon_home_screen
        AppIconSurface.DOCK -> Res.string.settings_app_icon_dock
        AppIconSurface.TASKBAR -> Res.string.settings_app_icon_taskbar
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
        AppIconSurface.DOCK -> Res.string.settings_app_icon_dock_description
        AppIconSurface.TASKBAR -> Res.string.settings_app_icon_taskbar_description
        AppIconSurface.WINDOW -> Res.string.settings_app_icon_window_description
        AppIconSurface.BROWSER_TAB -> Res.string.settings_app_icon_browser_tab_description
    }
)
