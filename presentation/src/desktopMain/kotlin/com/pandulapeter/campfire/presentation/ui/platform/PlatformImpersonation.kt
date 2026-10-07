/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.platform

import androidx.compose.ui.text.font.FontFamily

/**
 * The platform the desktop build draws its interface as, and the fonts that platform would set it in. The desktop app
 * never sets it, so it is always itself; the store screenshot tool (`tools/screenshots`) does, since it renders this
 * build offscreen as every platform's store listing shows the app. The platforms share every line of the interface
 * but what the expect declarations of this package decide - whether it is a desktop (and so the scale it is drawn at,
 * see `interfaceScale`), the store and the app icon the settings name, whether the library can be edited outside the
 * app - and what only the platform has: Android's wallpaper colors, which the color options offer next to the app's
 * own, and a phone's vibrator, which the metronome offers to tap the beat with. Those read this on every access, so it
 * has to be set before the first composition and left alone after it.
 */
object PlatformImpersonation {

    /** The platform the interface is drawn as, or null for the one it runs on. */
    var platform: ImpersonatedPlatform? = null

    /** What the interface is set in, in place of the system font a desktop finds for Material's default family. */
    var fontFamily: FontFamily? = null

    /** What tablature, grids and the editor are set in, in place of the desktop's own monospace font. */
    var monospaceFontFamily: FontFamily? = null
}

/** The platforms the store listings show, see [PlatformImpersonation]. */
enum class ImpersonatedPlatform {
    ANDROID,
    IOS,
    MACOS,
    WINDOWS,
}
