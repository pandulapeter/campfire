/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.theme

import com.pandulapeter.campfire.data.model.domain.UserPreferences

/**
 * The theme color the app icon is to be in: the one the user chose, unless they asked to keep the app's own icon
 * whatever the theme ([UserPreferences.isAppIconThemed]). Preferences that have not been read yet ask for the app's own,
 * which is what every platform shows until then anyway.
 */
val UserPreferences?.appIconThemeColor
    get() = if (this?.isAppIconThemed == true) themeColor else UserPreferences.ThemeColor.CAMPFIRE

/**
 * The color of the app icon that goes with a theme color, the one the platforms name their icon files by (its
 * [UserPreferences.ThemeColor.id]). Every color the app offers has an icon of its own, generated from the hand-drawn orange ones
 * by `app/generate_theme_icons.py`, except [UserPreferences.ThemeColor.SYSTEM]: only Android honors it, and has an icon
 * the system colors for it (see `AppIconSwitcher` in `:app:android`), and everywhere else the theme falls back to the
 * app's own palette, so the icon does too. So does a preference that has not been read yet.
 *
 * A new theme color needs an icon on every platform: a seed in that script, an alternate icon name in the Xcode
 * project, a launcher alias in the Android manifest, and an entry in the desktop's `AppIcon.kt` and Android's switcher.
 */
val UserPreferences.ThemeColor?.appIconColor
    get() = when (this) {
        null,
        UserPreferences.ThemeColor.SYSTEM -> UserPreferences.ThemeColor.CAMPFIRE
        else -> this
    }
