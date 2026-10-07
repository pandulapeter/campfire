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

import com.pandulapeter.campfire.data.model.domain.UserPreferences

/**
 * A Screenshot Bro row that shows several of the listing's shots at once, each on another device, in the order its
 * devices are imported in: the README's banners, the Play Store's feature graphic and the website's link preview.
 *
 * @property folder The name of the folder the shots are written to, which is the Screenshot Bro row's label.
 */
internal class Banner(
    val folder: String,
    val slots: List<Pair<Device, Shot>>,
)

/**
 * The two banners at the top of the README - the same five shots, in the dark theme from the phone to the Mac and in
 * the light one from the Mac back to the phone - Apple's product page header and search results images, the Play
 * Store's feature graphic, which is the Android phone's first
 * two shots, and the website's link preview, the song on the Mac with the library on the iPhone in front of it.
 */
internal val banners: List<Banner> by lazy {
    val slots = listOf(
        Device.ANDROID_PHONE to "01-songs",
        Device.IPHONE to "03-metronome",
        Device.IPAD to "02-song",
        Device.ANDROID_SMALL_TABLET to "04-setlists",
        Device.MAC to "05-editor",
    ).map { (device, id) -> device to shot(id) }
    listOf(
        Banner(
            folder = "GitHub - Banner 1",
            slots = slots.map { (device, shot) -> device to shot.inTheme(UserPreferences.UiMode.DARK) },
        ),
        Banner(
            folder = "GitHub - Banner 2",
            slots = slots.reversed().map { (device, shot) -> device to shot.inTheme(UserPreferences.UiMode.LIGHT) },
        ),
        Banner(
            folder = "Play Store - Banner",
            slots = listOf(Device.ANDROID_PHONE to shot("01-songs"), Device.ANDROID_PHONE to shot("02-song")),
        ),
        // Apple's product page header (21:9) and search results image (3:2): every Apple device at once on the first,
        // and the song on the iPad with the metronome on the iPhone on the second, all in the dark theme.
        Banner(
            folder = "App Store - Header",
            slots = listOf(
                Device.IPAD to shot("04-setlists").inTheme(UserPreferences.UiMode.DARK),
                Device.MAC to shot("02-song"),
                Device.IPHONE to shot("01-songs"),
            ),
        ),
        Banner(
            folder = "App Store - Search results",
            slots = listOf(
                Device.IPAD to shot("02-song"),
                Device.IPHONE to shot("03-metronome").inTheme(UserPreferences.UiMode.DARK),
            ),
        ),
        Banner(
            folder = "Website - Link preview",
            slots = listOf(Device.MAC to shot("02-song"), Device.IPHONE to shot("01-songs")),
        ),
    )
}

private fun shot(id: String) = shots.first { it.id == id }
