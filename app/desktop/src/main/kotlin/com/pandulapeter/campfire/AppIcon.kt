/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.painter.Painter
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.theme.appIconColor
import com.pandulapeter.campfire.presentation.ui.theme.appIconThemeColor
import com.pandulapeter.campfire.resources.Res
import com.pandulapeter.campfire.resources.app_icon
import com.pandulapeter.campfire.resources.app_icon_blue
import com.pandulapeter.campfire.resources.app_icon_gray
import com.pandulapeter.campfire.resources.app_icon_green
import com.pandulapeter.campfire.resources.app_icon_orange
import com.pandulapeter.campfire.resources.app_icon_pink
import com.pandulapeter.campfire.resources.app_icon_purple
import com.pandulapeter.campfire.resources.app_icon_red
import com.pandulapeter.campfire.resources.app_icon_teal
import com.pandulapeter.campfire.resources.app_icon_yellow
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * The app icon in the theme the user chose, for as long as the app runs: the icons of the packages (`appIcon.icns`,
 * `appIcon.ico`, the Store's logos and the Linux `.desktop` entry's) are files the system reads while the app is not
 * running, and stay the ones of the app's own color.
 *
 * What is returned is the window icon, which Linux puts on whatever its window manager draws for a window - the round
 * icon, in the chosen color. Windows and macOS keep the app's own: the taskbar button of a packaged app is drawn from
 * the package, and on macOS a window has no icon, while an image given to the Dock at runtime is a flat picture the
 * system cannot draw in its clear, tinted or dark icon styles, so it would stand out from every other icon there.
 *
 * @param viewModel Null until the window has created it, which is also a moment the preferences have not been read.
 */
@Composable
internal fun appIcon(viewModel: CampfireViewModel?): Painter {
    val userPreferences = viewModel?.userPreferences?.collectAsState()?.value
    // Neither platform offers a switch for it, and the preference may still be on from a build that did.
    val appIconColor = (if (isWindows || isMacOs) null else userPreferences).appIconThemeColor.appIconColor
    return painterResource(WINDOW_ICONS.getValue(appIconColor))
}

private val WINDOW_ICONS: Map<UserPreferences.ThemeColor, DrawableResource> = mapOf(
    UserPreferences.ThemeColor.CAMPFIRE to Res.drawable.app_icon,
    UserPreferences.ThemeColor.GRAY to Res.drawable.app_icon_gray,
    UserPreferences.ThemeColor.RED to Res.drawable.app_icon_red,
    UserPreferences.ThemeColor.ORANGE to Res.drawable.app_icon_orange,
    UserPreferences.ThemeColor.YELLOW to Res.drawable.app_icon_yellow,
    UserPreferences.ThemeColor.GREEN to Res.drawable.app_icon_green,
    UserPreferences.ThemeColor.TEAL to Res.drawable.app_icon_teal,
    UserPreferences.ThemeColor.BLUE to Res.drawable.app_icon_blue,
    UserPreferences.ThemeColor.PURPLE to Res.drawable.app_icon_purple,
    UserPreferences.ThemeColor.PINK to Res.drawable.app_icon_pink,
)
