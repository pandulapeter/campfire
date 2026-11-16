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

import kotlin.test.Test
import kotlin.test.assertEquals

class PictureFileNameTest {

    @Test
    fun `a name without a picture's extension gets the one its type says`() {
        assertEquals("1000012345.jpg", pictureFileName("1000012345", "image/jpeg"))
        assertEquals("IMG_0001.png", pictureFileName("IMG_0001", "image/png"))
        assertEquals("scan.gif", pictureFileName("scan", "image/gif"))
        assertEquals("sticker.webp", pictureFileName("sticker", "IMAGE/WEBP"))
    }

    @Test
    fun `the formats the phones convert get their own extensions`() {
        assertEquals("IMG_0002.heic", pictureFileName("IMG_0002", "image/heic"))
        assertEquals("IMG_0003.heif", pictureFileName("IMG_0003", "image/heif"))
        assertEquals("frame.avif", pictureFileName("frame", "image/avif"))
    }

    @Test
    fun `a name that already ends in a picture's extension is kept`() {
        assertEquals("IMG_0004.HEIC", pictureFileName("IMG_0004.HEIC", "image/heic"))
        assertEquals("photo.jpeg", pictureFileName("photo.jpeg", "image/jpeg"))
        assertEquals("photo.png", pictureFileName("photo.png", "image/jpeg"))
    }

    @Test
    fun `a type that is no picture's leaves the name alone`() {
        assertEquals("notes.txt", pictureFileName("notes.txt", "text/plain"))
        assertEquals("1000012345", pictureFileName("1000012345", null))
    }

    @Test
    fun `a blank name is called photo`() {
        assertEquals("photo.jpg", pictureFileName("", "image/jpeg"))
    }
}
