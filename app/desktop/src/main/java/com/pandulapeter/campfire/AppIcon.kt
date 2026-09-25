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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.painter.Painter
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.platform.appIconColor
import com.pandulapeter.campfire.presentation.ui.platform.appIconThemeColor
import com.pandulapeter.campfire.resources.Res
import com.pandulapeter.campfire.resources.app_icon
import com.pandulapeter.campfire.resources.app_icon_blue
import com.pandulapeter.campfire.resources.app_icon_green
import com.pandulapeter.campfire.resources.app_icon_orange
import com.pandulapeter.campfire.resources.app_icon_pink
import com.pandulapeter.campfire.resources.app_icon_purple
import com.pandulapeter.campfire.resources.app_icon_red
import com.pandulapeter.campfire.resources.app_icon_teal
import com.pandulapeter.campfire.resources.app_icon_yellow
import com.pandulapeter.campfire.resources.dock_icon_blue
import com.pandulapeter.campfire.resources.dock_icon_campfire
import com.pandulapeter.campfire.resources.dock_icon_green
import com.pandulapeter.campfire.resources.dock_icon_orange
import com.pandulapeter.campfire.resources.dock_icon_pink
import com.pandulapeter.campfire.resources.dock_icon_purple
import com.pandulapeter.campfire.resources.dock_icon_red
import com.pandulapeter.campfire.resources.dock_icon_teal
import com.pandulapeter.campfire.resources.dock_icon_yellow
import java.awt.Taskbar
import javax.imageio.ImageIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.getDrawableResourceBytes
import org.jetbrains.compose.resources.getSystemResourceEnvironment
import org.jetbrains.compose.resources.painterResource

/**
 * The app icon in the theme the user chose, for as long as the app runs: the icons of the packages (`appIcon.icns`,
 * `appIcon.ico`, the Store's logos and the Linux `.desktop` entry's) are files the system reads while the app is not
 * running, and stay the gray ones of the app's own color.
 *
 * What is returned is the window icon, which Windows puts on the title bar and the taskbar button and Linux on
 * whatever its window manager draws for a window - the round icon, in the chosen color. On macOS a window has no icon,
 * and the one in the Dock is the application's; that is set here as well.
 *
 * @param viewModel Null until the window has created it, which is also a moment the preferences have not been read.
 */
@Composable
internal fun appIcon(viewModel: CampfireViewModel?): Painter {
    val userPreferences = viewModel?.userPreferences?.collectAsState()?.value
    val appIconColor = userPreferences.appIconThemeColor.appIconColor
    // An unread preference says nothing about the theme, and the packaged icon the Dock is showing until then is the
    // better guess.
    if (userPreferences != null && isDockIconSupported) {
        val dockIcon = DOCK_ICONS.getValue(appIconColor)
        LaunchedEffect(dockIcon) {
            val image = withContext(Dispatchers.IO) {
                getDrawableResourceBytes(getSystemResourceEnvironment(), dockIcon).inputStream().use(ImageIO::read)
            }
            Taskbar.getTaskbar().iconImage = image
        }
    }
    return painterResource(WINDOW_ICONS.getValue(appIconColor))
}

/** True on macOS, the one platform whose AWT can set the icon of the application rather than of a window. */
private val isDockIconSupported by lazy { Taskbar.isTaskbarSupported() && Taskbar.getTaskbar().isSupported(Taskbar.Feature.ICON_IMAGE) }

private val WINDOW_ICONS: Map<UserPreferences.ThemeColor, DrawableResource> = mapOf(
    UserPreferences.ThemeColor.CAMPFIRE to Res.drawable.app_icon,
    UserPreferences.ThemeColor.RED to Res.drawable.app_icon_red,
    UserPreferences.ThemeColor.ORANGE to Res.drawable.app_icon_orange,
    UserPreferences.ThemeColor.YELLOW to Res.drawable.app_icon_yellow,
    UserPreferences.ThemeColor.GREEN to Res.drawable.app_icon_green,
    UserPreferences.ThemeColor.TEAL to Res.drawable.app_icon_teal,
    UserPreferences.ThemeColor.BLUE to Res.drawable.app_icon_blue,
    UserPreferences.ThemeColor.PURPLE to Res.drawable.app_icon_purple,
    UserPreferences.ThemeColor.PINK to Res.drawable.app_icon_pink,
)

private val DOCK_ICONS: Map<UserPreferences.ThemeColor, DrawableResource> = mapOf(
    UserPreferences.ThemeColor.CAMPFIRE to Res.drawable.dock_icon_campfire,
    UserPreferences.ThemeColor.RED to Res.drawable.dock_icon_red,
    UserPreferences.ThemeColor.ORANGE to Res.drawable.dock_icon_orange,
    UserPreferences.ThemeColor.YELLOW to Res.drawable.dock_icon_yellow,
    UserPreferences.ThemeColor.GREEN to Res.drawable.dock_icon_green,
    UserPreferences.ThemeColor.TEAL to Res.drawable.dock_icon_teal,
    UserPreferences.ThemeColor.BLUE to Res.drawable.dock_icon_blue,
    UserPreferences.ThemeColor.PURPLE to Res.drawable.dock_icon_purple,
    UserPreferences.ThemeColor.PINK to Res.drawable.dock_icon_pink,
)
