/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.screenshots

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.ui.platform.ImpersonatedPlatform

/**
 * One row of the Screenshot Bro project: the screen its device frame holds, in the pixels the frame expects and the
 * density the platform draws that screen at, and the system chrome around the app there.
 *
 * The pixel sizes are the screens of the frames' devices, so an image fills its frame exactly: the iPhone 17 Pro Max,
 * the iPhone Duo's inner display unfolded and on its side, the 13" iPad Pro, the Pixel 9, and the 14" and 16" MacBook Pros
 * the desktop rows are framed in. The Android tablet
 * frame is a generic one, whose screen (556 by 916 inside its 600 by 960) is a little narrower than a 16:10 tablet's,
 * and which fills it by cropping the image's sides, so its shots are rendered at that screen's own shape. The densities are the platforms' own for those screens (2x on a Retina display, which is also Windows at 200%),
 * which decides how much of the app fits, so the shots show as much of a song as the device would.
 *
 * @property folder The name of the folder the shots are written to, which is the Screenshot Bro row's label.
 */
internal enum class Device(
    val folder: String,
    val platform: ImpersonatedPlatform,
    val widthPx: Int,
    val heightPx: Int,
    val density: Float,
    val chrome: SystemChrome,
    val isInListing: Boolean = true,
) {
    ANDROID_PHONE(
        folder = "Play Store - Android Phone",
        platform = ImpersonatedPlatform.ANDROID,
        widthPx = 1080,
        heightPx = 2424,
        density = 2.625f,
        chrome = SystemChrome.AndroidPhone,
    ),
    ANDROID_SMALL_TABLET(
        folder = "Play Store - Android Small Tablet",
        platform = ImpersonatedPlatform.ANDROID,
        widthPx = 1554,
        heightPx = 2560,
        density = 2f,
        chrome = SystemChrome.AndroidTablet,
    ),
    ANDROID_LARGE_TABLET(
        folder = "Play Store - Android Large Tablet",
        platform = ImpersonatedPlatform.ANDROID,
        widthPx = 2560,
        heightPx = 1554,
        density = 2f,
        chrome = SystemChrome.AndroidTablet,
    ),
    CHROMEBOOK(
        folder = "Play Store - Chromebook",
        platform = ImpersonatedPlatform.ANDROID,
        widthPx = 3024,
        heightPx = 1964,
        density = 2f,
        chrome = SystemChrome.ChromeOs,
    ),
    IPHONE(
        folder = "App Store - iPhone",
        platform = ImpersonatedPlatform.IOS,
        widthPx = 1320,
        heightPx = 2868,
        density = 3f,
        chrome = SystemChrome.IPhone,
    ),

    /**
     * The iPhone Duo opened into its inner display and held on its side, at the iPhone's 3x, where iOS lays the app out
     * like an iPad's and draws the iPad's status bar over it.
     */
    IPHONE_DUO(
        folder = "App Store - iPhone Duo",
        platform = ImpersonatedPlatform.IOS,
        widthPx = 2853,
        heightPx = 2007,
        density = 3f,
        chrome = SystemChrome.IPad,
    ),
    IPAD(
        folder = "App Store - iPad",
        platform = ImpersonatedPlatform.IOS,
        widthPx = 2752,
        heightPx = 2064,
        density = 2f,
        chrome = SystemChrome.IPad,
    ),
    MAC(
        folder = "Mac App Store - Mac",
        platform = ImpersonatedPlatform.MACOS,
        widthPx = 3456,
        heightPx = 2234,
        density = 2f,
        chrome = SystemChrome.MacOs,
    ),
    WINDOWS(
        folder = "Microsoft Store - Windows",
        platform = ImpersonatedPlatform.WINDOWS,
        widthPx = 3456,
        heightPx = 2234,
        density = 2f,
        chrome = SystemChrome.Windows,
    ),

    /**
     * The website's laptop: the Mac app's window and nothing around it, which the page frames itself, at the density
     * that gives a window of a 13" laptop's size and fits a song in three columns, as the page has always shown it.
     */
    LAPTOP(
        folder = "Website",
        platform = ImpersonatedPlatform.MACOS,
        widthPx = 2000,
        heightPx = 1255,
        density = 1.5f,
        chrome = SystemChrome.None,
        isInListing = false,
    );

    val isPhone get() = this == ANDROID_PHONE || this == IPHONE

    val width: Dp get() = (widthPx / density).dp

    val height: Dp get() = (heightPx / density).dp
}
