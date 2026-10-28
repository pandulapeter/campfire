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

import com.pandulapeter.campfire.screenshots.Composition.Background
import com.pandulapeter.campfire.screenshots.Composition.Caption
import com.pandulapeter.campfire.screenshots.Composition.Logo
import com.pandulapeter.campfire.screenshots.Composition.Output
import com.pandulapeter.campfire.screenshots.Composition.Screen

/**
 * Every [Composition], laid out as the owner arranged them by hand: each device has a box, tilt included, and its frame
 * is as large as fits it. A screen's slot is its
 * place in its [Banner]'s slots, and the elements are listed back to front.
 */
internal val compositions = listOf(
    // The README's two banners: five devices across a panorama of three images, the dark shots on orange and the light
    // ones, in the other order, on purple.
    Composition(
        widthPx = 7200,
        heightPx = 1800,
        background = Background.RADIAL_ORANGE,
        banner = "GitHub - Banner 1",
        elements = listOf(
            Screen(slot = 4, kind = FrameKind.LAPTOP, x = 4576, y = 36, width = 2624, height = 1729),
            Screen(slot = 2, kind = FrameKind.TABLET, x = 2120, y = 123, width = 2027, height = 1554),
            Logo(x = 53, y = 1255, width = 500, height = 500),
            Screen(slot = 1, kind = FrameKind.PHONE, x = 1354, y = 126, width = 979, height = 1997),
            Screen(slot = 0, kind = FrameKind.PHONE, x = 578, y = 181, width = 933, height = 1972, rotation = -8f),
            Screen(slot = 3, kind = FrameKind.TABLET, x = 3841, y = 38, width = 1166, height = 1865, rotation = 4f),
        ),
        outputs = (1..3).map { Output("%02d.webp".format(it), isReadme = true) },
        scale = 0.5f,
    ),
    Composition(
        widthPx = 7200,
        heightPx = 1800,
        background = Background.RADIAL_PURPLE,
        banner = "GitHub - Banner 2",
        elements = listOf(
            Screen(slot = 0, kind = FrameKind.LAPTOP, x = 0, y = 36, width = 2624, height = 1729),
            Screen(slot = 2, kind = FrameKind.TABLET, x = 3264, y = 123, width = 2027, height = 1554),
            Screen(slot = 3, kind = FrameKind.PHONE, x = 5155, y = 107, width = 979, height = 1997),
            Screen(slot = 4, kind = FrameKind.PHONE, x = 5988, y = 190, width = 933, height = 1972, rotation = 8f),
            Screen(slot = 1, kind = FrameKind.TABLET, x = 2296, y = 61, width = 1181, height = 1890, rotation = -4f),
        ),
        outputs = (4..6).map { Output("%02d.webp".format(it), isReadme = true) },
        scale = 0.5f,
    ),
    // The Play Store's feature graphic: the library and a song on the Android phone, beside the mark.
    Composition(
        widthPx = 1024,
        heightPx = 500,
        background = Background.LINEAR,
        banner = "Play Store - Banner",
        elements = listOf(
            Screen(slot = 0, kind = FrameKind.PHONE, x = 328, y = 40, width = 399, height = 843, rotation = -12.25f),
            Logo(x = 49, y = 308, width = 154, height = 154),
            Screen(slot = 1, kind = FrameKind.PHONE, x = 612, y = 7, width = 399, height = 843),
        ),
        outputs = listOf(Output("google-play/{language}/feature-graphic.png")),
    ),
    Composition(
        widthPx = 512,
        heightPx = 512,
        background = Background.LINEAR,
        elements = listOf(Logo(x = 63, y = 63, width = 385, height = 385)),
        outputs = listOf(Output("google-play/icon.png", hasAlpha = true)),
    ),
    // Apple's product page header (21:9) and search results image (3:2), for iOS 27 and later.
    Composition(
        widthPx = 3840,
        heightPx = 1646,
        background = Background.RADIAL_PURPLE,
        banner = "App Store - Header",
        elements = listOf(
            Screen(slot = 1, kind = FrameKind.LAPTOP, x = 845, y = -47, width = 2150, height = 1417),
            Screen(slot = 0, kind = FrameKind.TABLET, x = 380, y = 511, width = 1350, height = 1035),
            Screen(slot = 2, kind = FrameKind.PHONE, x = 2762, y = 437, width = 580, height = 1184),
        ),
        outputs = listOf(Output("app-store/{language}/header.png")),
    ),
    Composition(
        widthPx = 3840,
        heightPx = 2560,
        background = Background.RADIAL_PURPLE,
        banner = "App Store - Search results",
        elements = listOf(
            Screen(slot = 1, kind = FrameKind.PHONE, x = 2640, y = 480, width = 1000, height = 2041),
            Screen(slot = 0, kind = FrameKind.TABLET, x = 250, y = 440, width = 2700, height = 2070),
            Caption(textId = "search-results", size = 140, x = 120, y = 80, width = 3600, height = 260),
        ),
        outputs = listOf(Output("app-store/{language}/search-results.png")),
    ),
    // Microsoft's poster and box art: the mark alone.
    Composition(
        widthPx = 1440,
        heightPx = 2160,
        background = Background.LINEAR,
        elements = listOf(Logo(x = 358, y = 691, width = 724, height = 779)),
        outputs = listOf(Output("microsoft-store/poster-art.png")),
    ),
    Composition(
        widthPx = 2160,
        heightPx = 2160,
        background = Background.LINEAR,
        elements = listOf(Logo(x = 578, y = 578, width = 1003, height = 1003)),
        outputs = listOf(Output("microsoft-store/box-art.png")),
    ),
    // The website's link preview, which the website-screenshots skill turns into its og-image.jpg.
    Composition(
        widthPx = 1200,
        heightPx = 630,
        background = Background.LINEAR,
        banner = "Website - Link preview",
        elements = listOf(
            Screen(slot = 0, kind = FrameKind.LAPTOP, x = 169, y = 88, width = 1018, height = 671),
            Logo(x = 23, y = 420, width = 181, height = 181),
            Screen(slot = 1, kind = FrameKind.PHONE, x = 768, y = 35, width = 466, height = 951, rotation = 9.1f),
        ),
        outputs = listOf(Output("link-preview.png")),
    ),
)
