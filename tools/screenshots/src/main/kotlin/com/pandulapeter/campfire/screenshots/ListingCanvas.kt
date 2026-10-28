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

/**
 * Where a listing [Device]'s images go and what they are drawn on: the [store]'s folder in `documentation/store-listings`,
 * the [name] its images start with there, the canvas the store asks for, in pixels, and the [frame] its screen is drawn
 * in. The canvases are the sizes the stores take for the device (Apple's 6.9" iPhone and 13" iPad, a Mac's 2880 by
 * 1800), and the Play Store's and Microsoft's are sizes they accept for its kind of device.
 */
internal class ListingCanvas(
    val store: String,
    val name: String,
    val widthPx: Int,
    val heightPx: Int,
    val frame: FrameKind,
) {
    val isLandscape get() = widthPx > heightPx
}

/** The listing canvas of a device whose shots are in the store listings, null for the website's and the video's. */
internal val Device.listingCanvas
    get() = when (this) {
        Device.ANDROID_PHONE -> ListingCanvas(GOOGLE_PLAY, "phone", 1440, 2560, FrameKind.PHONE)
        Device.ANDROID_SMALL_TABLET -> ListingCanvas(GOOGLE_PLAY, "small-tablet", 1200, 1920, FrameKind.TABLET)
        Device.ANDROID_LARGE_TABLET -> ListingCanvas(GOOGLE_PLAY, "large-tablet", 2752, 2064, FrameKind.TABLET)
        Device.CHROMEBOOK -> ListingCanvas(GOOGLE_PLAY, "chromebook", 1920, 1080, FrameKind.LAPTOP)
        Device.IPHONE -> ListingCanvas(APP_STORE, "iphone", 1320, 2868, FrameKind.PHONE)
        Device.IPHONE_DUO -> ListingCanvas(APP_STORE, "iphone-duo", 2853, 2007, FrameKind.FOLDABLE)
        Device.IPAD -> ListingCanvas(APP_STORE, "ipad", 2752, 2064, FrameKind.TABLET)
        Device.MAC -> ListingCanvas("mac-app-store", "mac", 2880, 1800, FrameKind.LAPTOP)
        Device.WINDOWS -> ListingCanvas("microsoft-store", "windows", 2880, 1800, FrameKind.LAPTOP)
        Device.IPAD_PORTRAIT, Device.LAPTOP -> null
    }

private const val GOOGLE_PLAY = "google-play"
private const val APP_STORE = "app-store"
