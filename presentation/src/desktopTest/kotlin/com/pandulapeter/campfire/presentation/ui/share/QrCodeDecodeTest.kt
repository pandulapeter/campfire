/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.share

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.decoder.Decoder
import com.pandulapeter.campfire.presentation.ui.share.QrCode.Level
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** What the encoder draws, read back by ZXing, a decoder written independently from the same standard. */
internal class QrCodeDecodeTest {
    @Test fun `every version, level, and mask decodes to the bytes encoded`() {
        val random = Random(18004)
        for (version in QrCode.MIN_VERSION..QrCode.MAX_VERSION) {
            for (level in Level.entries) {
                val data = random.nextBytes(qrByteCapacity(version, level))
                for (mask in 0 until QrCode.MASK_COUNT) {
                    val code = QrCode.encode(data = data, version = version, level = level, mask = mask)
                    val result = Decoder().decode(code.toBitMatrix())
                    val context = "version $version-$level, mask $mask"
                    assertEquals(level.name, result.ecLevel, context)
                    assertContentEquals(data, result.byteSegments.reduce { all, segment -> all + segment }, context)
                }
            }
        }
    }

    @Test fun `a short payload decodes in every symbol`() {
        val data = "chords".encodeToByteArray()
        for (version in QrCode.MIN_VERSION..QrCode.MAX_VERSION) {
            val code = QrCode.encode(data = data, version = version, level = Level.H, mask = null)
            assertContentEquals(data, Decoder().decode(code.toBitMatrix()).byteSegments.single(), "version $version")
        }
    }

    @Test fun `text with characters beyond ASCII decodes as UTF-8`() {
        val text = "https://campfire-songbook.com/s/#1.song.abcdefghijklmnop.Xy_-12.key.Ősz%20van — ♯♭"
        val code = assertNotNull(QrCode.encode(text))
        val result = Decoder().decode(code.toBitMatrix(), mapOf(DecodeHintType.CHARACTER_SET to "UTF-8"))
        assertEquals(text, result.text)
    }

    @Test fun `the PNG reads back as an image a scanner decodes`() = runBlocking {
        val text = "https://campfire-songbook.com/s/#1.setlist.abcdefghijklmnop.AAAbbbCCCdddEEEfff0123456789.st0rq9zx4w1ek2r8yh" +
            "5v6c3pmn.Sunday%20evening%20at%20the%20lake"
        val code = assertNotNull(QrCode.encode(text))
        val image = assertNotNull(ImageIO.read(ByteArrayInputStream(qrCodePng(code, moduleSize = 8))))
        assertEquals((code.size + 8) * 8, image.width)
        assertEquals(image.width, image.height)
        assertEquals(0xFFFFFF, image.getRGB(0, 0) and 0xFFFFFF, "The quiet zone is white.")
        assertEquals(0, image.getRGB(32, 32) and 0xFFFFFF, "The top left finder's corner is black.")
        val pixels = image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(image.width, image.height, pixels)))
        assertEquals(text, QRCodeReader().decode(bitmap).text)
    }

    @Test fun `a PNG with one pixel to a module still decodes`() = runBlocking {
        val code = assertNotNull(QrCode.encode("one pixel"))
        val image = assertNotNull(ImageIO.read(ByteArrayInputStream(qrCodePng(code, moduleSize = 1))))
        assertEquals(code.size + 8, image.width)
        val pixels = image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(image.width, image.height, pixels)))
        assertEquals("one pixel", QRCodeReader().decode(bitmap, mapOf(DecodeHintType.PURE_BARCODE to true)).text)
    }

    private fun QrCode.toBitMatrix() = BitMatrix(size).also { matrix ->
        for (y in 0 until size) {
            for (x in 0 until size) if (isDark(x, y)) matrix.set(x, y)
        }
    }
}
