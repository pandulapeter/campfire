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
import androidx.compose.ui.graphics.painter.Painter
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_phone
import com.pandulapeter.campfire.presentation.resources.settings_user_interface_theme_color_blue
import com.pandulapeter.campfire.presentation.resources.settings_user_interface_theme_color_campfire
import com.pandulapeter.campfire.presentation.resources.settings_user_interface_theme_color_green
import com.pandulapeter.campfire.presentation.resources.settings_user_interface_theme_color_orange
import com.pandulapeter.campfire.presentation.resources.settings_user_interface_theme_color_pink
import com.pandulapeter.campfire.presentation.resources.settings_user_interface_theme_color_purple
import com.pandulapeter.campfire.presentation.resources.settings_user_interface_theme_color_red
import com.pandulapeter.campfire.presentation.resources.settings_user_interface_theme_color_system
import com.pandulapeter.campfire.presentation.resources.settings_user_interface_theme_color_teal
import com.pandulapeter.campfire.presentation.resources.settings_user_interface_theme_color_yellow
import com.pandulapeter.campfire.presentation.resources.settings_user_interface_theme_dark
import com.pandulapeter.campfire.presentation.resources.settings_user_interface_theme_light
import com.pandulapeter.campfire.presentation.resources.settings_user_interface_theme_system_default
import com.pandulapeter.campfire.presentation.ui.theme.isDarkTheme
import com.pandulapeter.campfire.presentation.ui.theme.themeColorOptions
import org.jetbrains.compose.resources.painterResource

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

/**
 * Every color the theme can be painted in, as the settings screen and the welcome sheet both offer it.
 *
 * @param uiMode The mode the app is drawn in, which decides the half of each palette the discs are filled with.
 */
@Composable
internal fun ThemeColorChoice(
    modifier: Modifier = Modifier,
    uiMode: UserPreferences.UiMode?,
    shouldApplyPadding: Boolean = true,
    selected: UserPreferences.ThemeColor?,
    onSelected: (UserPreferences.ThemeColor) -> Unit,
) {
    // Use the opposite half of each palette for the discs: brighter colors in light mode and deeper colors in
    // dark mode. Keep each disc's onPrimary from the same half so its icon stays legible.
    val isDarkTheme = uiMode.isDarkTheme()
    ColorChoice(
        modifier = modifier,
        options = themeColorOptions().map { (themeColor, colorSchemePair) ->
            val colorScheme = if (isDarkTheme) colorSchemePair.light else colorSchemePair.dark
            ColorChoiceOption(
                value = themeColor,
                color = colorScheme.primary,
                contentColor = colorScheme.onPrimary,
                label = themeColor.label(),
                icon = themeColor.icon(),
            )
        },
        shouldApplyPadding = shouldApplyPadding,
        selected = selected,
        onSelected = onSelected,
    )
}

/**
 * What a color offered by the theme is called. It is only ever read out by an accessibility service, since the
 * choice shows each color as itself.
 */
@Composable
private fun UserPreferences.ThemeColor.label() = stringResource(
    when (this) {
        UserPreferences.ThemeColor.CAMPFIRE -> Res.string.settings_user_interface_theme_color_campfire
        UserPreferences.ThemeColor.SYSTEM -> Res.string.settings_user_interface_theme_color_system
        UserPreferences.ThemeColor.RED -> Res.string.settings_user_interface_theme_color_red
        UserPreferences.ThemeColor.ORANGE -> Res.string.settings_user_interface_theme_color_orange
        UserPreferences.ThemeColor.YELLOW -> Res.string.settings_user_interface_theme_color_yellow
        UserPreferences.ThemeColor.GREEN -> Res.string.settings_user_interface_theme_color_green
        UserPreferences.ThemeColor.TEAL -> Res.string.settings_user_interface_theme_color_teal
        UserPreferences.ThemeColor.BLUE -> Res.string.settings_user_interface_theme_color_blue
        UserPreferences.ThemeColor.PURPLE -> Res.string.settings_user_interface_theme_color_purple
        UserPreferences.ThemeColor.PINK -> Res.string.settings_user_interface_theme_color_pink
    }
)

/**
 * The icon a color offered by the theme carries while it is not selected, for the one whose color is not what picks it
 * out: the palette the operating system hands over, which is whatever the wallpaper made it. The rest are only a
 * color - the app's own gray included - and a glyph on each of them would say nothing the disc does not.
 */
@Composable
private fun UserPreferences.ThemeColor.icon(): Painter? = when (this) {
    UserPreferences.ThemeColor.SYSTEM -> painterResource(Res.drawable.ic_phone)
    else -> null
}
