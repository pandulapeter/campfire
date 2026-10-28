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

import com.pandulapeter.campfire.data.model.preferences.UserPreferences

/**
 * A set of the listing's shots that one image shows at once, each on another device, which a [Composition] lays out: the
 * README's banners, the Play Store's feature graphic, Apple's header and search results images, and the website's link
 * preview.
 *
 * @property folder The name of the folder the shots are rendered into.
 * @property isLocalized Whether the shots are rendered in every language of the app, each into a folder of its own,
 *   for an image the stores show in every language.
 */
internal class Banner(
    val folder: String,
    val slots: List<Pair<Device, Shot>>,
    val isLocalized: Boolean = false,
) {
    /** The name the render of the slot at [index] is written under, numbered by its place in [slots]. */
    fun fileName(index: Int) = slots[index].let { (device, shot) -> "%02d-%s-%s".format(index + 1, shot.id, device.name.lowercase()) }
}

/**
 * The two banners at the top of the README - the same five shots, in the dark theme from the phone to the Mac and in
 * the light one from the Mac back to the phone - Apple's product page header and search results images, the Play
 * Store's feature graphic, which is the Android phone's first
 * two shots, and the website's link preview, the song on the Mac with the library on the iPhone in front of it.
 */
internal val banners: List<Banner> by lazy {
    val slots = listOf(
        Device.ANDROID_PHONE to "01-songs",
        Device.IPHONE to "03-metronome-tuner",
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
            isLocalized = true,
        ),
        // Apple's product page header (21:9) and search results image (3:2): every Apple device at once on the first,
        // and the song on the iPad with its metronome and tuner on the iPhone on the second, all in the dark theme.
        Banner(
            folder = "App Store - Header",
            slots = listOf(
                Device.IPAD to shot("04-setlists").inTheme(UserPreferences.UiMode.DARK),
                Device.MAC to shot("02-song"),
                Device.IPHONE to shot("01-songs"),
            ),
            isLocalized = true,
        ),
        Banner(
            folder = "App Store - Search results",
            slots = listOf(
                Device.IPAD to shot("02-song"),
                Device.IPHONE to shot("03-metronome-tuner").inTheme(UserPreferences.UiMode.DARK),
            ),
            isLocalized = true,
        ),
        Banner(
            folder = "Website - Link preview",
            slots = listOf(Device.MAC to shot("02-song"), Device.IPHONE to shot("01-songs")),
        ),
    )
}

private fun shot(id: String) = shots.first { it.id == id }

/** The banner rendered into [folder]. */
internal fun banner(folder: String) = banners.first { it.folder == folder }
