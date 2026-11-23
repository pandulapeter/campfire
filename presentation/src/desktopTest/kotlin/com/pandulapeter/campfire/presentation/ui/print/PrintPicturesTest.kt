/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.print

import com.pandulapeter.campfire.presentation.ui.registerComposeUiRuntime
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import coil3.PlatformContext
import com.pandulapeter.campfire.presentation.ui.pictures.SongImageLoader
import com.pandulapeter.campfire.presentation.ui.pictures.SongImageSize
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.zip.Inflater
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A PDF page holding a photo, and one holding a missing picture's sentence, rasterized and encoded for real. */
internal class PrintPicturesTest {

    init {
        registerComposeUiRuntime()
    }

    /** A gradient from black to white, left to right, whose shades sixteen grays would band. */
    private val photo = ByteArrayOutputStream().also { output ->
        val image = BufferedImage(256, 64, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until 64) for (x in 0 until 256) image.setRGB(x, y, Color(x, x, x).rgb)
        ImageIO.write(image, "png", output)
    }.toByteArray()

    private class Library(private val pictures: Map<String, ByteArray>) : SongImageLoader {
        private val _sizes = MutableStateFlow(emptyMap<String, SongImageSize>())
        override val cacheKey = "test"
        override val sizes = _sizes
        override suspend fun loadSizes(names: Set<String>) = _sizes.update { sizes ->
            sizes + names.associateWith { name -> pictures[name]?.let { SongImageSize.Known(256, 64) } ?: SongImageSize.Missing }
        }
        override suspend fun loadImage(name: String) = pictures[name]
    }

    @Test fun `a page holding a photo is written in 256 grays with the photo's shades, a page without one in sixteen`() = runBlocking {
        val pictures = PrintPictures(Library(mapOf("photo.png" to photo)), PlatformContext.INSTANCE, this)
        val renderer = PrintRenderer(TextMeasurer(createFontFamilyResolver(), LocaleList("en"), Density(1f), LayoutDirection.Ltr), pictures = pictures)
        val photoPage = PrintPage(texts = emptyList(), images = listOf(PrintImage("photo.png", x = 10f, y = 10f, width = 256f, height = 64f)))
        val textPage = PrintPage(texts = listOf(PrintText("Image not found: gone.jpg", 10f, 10f, PrintStyle(12f), isSelectable = false)))
        val bytes = renderer.pdfBytes(PrintDocument(width = 300f, height = 100f, pages = listOf(photoPage, textPage)), "Pictures")
        val text = String(bytes, Charsets.ISO_8859_1)
        assertEquals(listOf("8", "4"), Regex("""/BitsPerComponent (\d+)""").findAll(text).map { it.groupValues[1] }.toList())
        val photoPixels = inflate(imageStreams(bytes).first())
        // Three pixels a point: the photo's row through its middle, from its left edge to its right.
        val row = (10 + 32) * 3 * 900
        val shades = (30 until 30 + 256 * 3).map { photoPixels[row + it].toInt() and 255 }.toSet()
        assertTrue(shades.size > 100, "The photo printed in only ${shades.size} shades.")
    }

    private fun imageStreams(bytes: ByteArray): List<ByteArray> {
        val text = String(bytes, Charsets.ISO_8859_1)
        return Regex("""/Subtype /Image [^>]*/Length (\d+) >>\nstream\n""").findAll(text).map { match ->
            bytes.copyOfRange(match.range.last + 1, match.range.last + 1 + match.groupValues[1].toInt())
        }.toList()
    }

    private fun inflate(stream: ByteArray): ByteArray {
        val inflater = Inflater()
        inflater.setInput(stream)
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        while (!inflater.finished()) output.write(buffer, 0, inflater.inflate(buffer))
        inflater.end()
        return output.toByteArray()
    }
}
