/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.formats.image

import com.pandulapeter.campfire.data.formats.image.TestImages.ascii
import com.pandulapeter.campfire.data.formats.image.TestImages.bytes
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class ImageFormatsTest {

    @Test
    fun `HEIC, HEIF, and AVIF pictures are refused images`() {
        for (picture in listOf(ftyp("heic", "mif1", "heic"), ftyp("mif1", "mif1", "miaf"), ftyp("avif", "avif", "mif1", "miaf"), ftyp("isom", "avis"))) {
            assertTrue(ImageFormats.isRefusedImage(picture), picture.decodeToString())
            assertNull(ImageInfo.of(picture))
        }
    }

    @Test
    fun `a video is no refused image`() {
        assertFalse(ImageFormats.isRefusedImage(ftyp("isom", "isom", "iso2", "mp41")))
        assertFalse(ImageFormats.isRefusedImage(ftyp("qt  ")))
    }

    @Test
    fun `a brand past the end of its box is not read`() {
        val picture = bytes(0, 0, 0, 16) + ascii("ftypisom") + bytes(0, 0, 0, 0) + ascii("heic")
        assertFalse(ImageFormats.isRefusedImage(picture))
    }

    @Test
    fun `an SVG picture is a refused image`() {
        val pictures = listOf(
            "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 10 10\"/>",
            "<svg>",
            "﻿\n  <?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<!-- Made by hand -->\n" +
                "<!DOCTYPE svg PUBLIC \"-//W3C//DTD SVG 1.1//EN\" \"http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd\">\n<svg\nwidth=\"10\">",
        )
        for (picture in pictures) {
            assertTrue(ImageFormats.isRefusedImage(ascii(picture)), picture)
            assertNull(ImageInfo.of(ascii(picture)))
        }
    }

    @Test
    fun `other text is no refused image`() {
        val texts = listOf(
            "",
            "<?xml version=\"1.0\"?><html><svg></svg></html>",
            "<svgfont>",
            "{title: <svg>}",
            "<?xml version=\"1.0\"",
            "<!-- <svg> -->",
            "<?xml version=\"1.0\"?>" + " ".repeat(5_000) + "<svg>",
        )
        for (text in texts) assertFalse(ImageFormats.isRefusedImage(ascii(text)), text)
    }

    @Test
    fun `the shown formats and random bytes are no refused image`() {
        assertFalse(ImageFormats.isRefusedImage(TestImages.png(10, 10).bytes))
        assertFalse(ImageFormats.isRefusedImage(TestImages.jpeg(10, 10).bytes))
        assertFalse(ImageFormats.isRefusedImage(TestImages.webpExtended(10, 10).bytes))
        val random = Random(3)
        repeat(2_000) { round -> assertFalse(ImageFormats.isRefusedImage(random.nextBytes(random.nextInt(0, 64))), "Round $round") }
    }

    @Test
    fun `every format names its extension and type`() {
        for (format in ImageFormat.entries) {
            assertTrue(format.extension.startsWith("."), format.name)
            assertTrue(format.mimeType.startsWith("image/"), format.name)
        }
    }

    private fun ftyp(major: String, vararg compatible: String): ByteArray {
        val body = ascii("ftyp$major") + bytes(0, 0, 0, 0) + compatible.fold(ByteArray(0)) { all, brand -> all + ascii(brand) }
        val size = body.size + 4
        return bytes(0, 0, size shr 8, size) + body + ByteArray(32)
    }
}
