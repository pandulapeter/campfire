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

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.yield
import kotlin.coroutines.coroutineContext
import kotlin.math.ceil

internal class PrintRenderer(private val measurer: TextMeasurer, private val fontFamily: FontFamily = FontFamily.Monospace) {
    private val cache = mutableMapOf<Triple<String, Int, Boolean>, TextLayoutResult>()

    private fun text(value: String, size: Int, bold: Boolean): TextLayoutResult {
        if (cache.size >= 512) cache.clear()
        return cache.getOrPut(Triple(value, size, bold)) {
            measurer.measure(
                text = value,
                style = TextStyle(color = Color.Black, fontFamily = fontFamily, fontSize = size.sp,
                    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, fontFeatureSettings = "liga=0"),
                softWrap = false,
                density = Density(1f, 1f),
                layoutDirection = LayoutDirection.Ltr,
            )
        }
    }

    fun width(value: String, size: Int, bold: Boolean): Float {
        // Measuring intermediate substrings must not keep all of them alive for the life of a large setlist.
        return measurer.measure(value, TextStyle(fontFamily = fontFamily, fontSize = size.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, fontFeatureSettings = "liga=0"),
            softWrap = false, density = Density(1f, 1f), layoutDirection = LayoutDirection.Ltr).size.width.toFloat()
    }

    fun draw(scope: DrawScope, page: PrintPage, scale: Float) = with(scope) {
        drawRect(Color.White)
        scale(scale, scale, pivot = Offset.Zero) {
            page.texts.forEach { item -> drawText(text(item.text, item.size, item.bold), topLeft = Offset(item.x, item.y)) }
        }
    }

    suspend fun pdf(document: PrintDocument): ByteArray {
        val writer = PrintPdfWriter(document.width, document.height)
        document.pages.forEach { page ->
            coroutineContext.ensureActive()
            val scale = 3f // 216 dpi: text remains sharp at its physical print size.
            val width = ceil(document.width * scale).toInt()
            val height = ceil(document.height * scale).toInt()
            val bitmap = ImageBitmap(width, height)
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(width.toFloat(), height.toFloat())) {
                draw(this, page, scale)
            }
            val pixels = IntArray(width)
            val gray = ByteArray(width * height)
            for (row in 0 until height) {
                if (row % 128 == 0) { coroutineContext.ensureActive(); yield() }
                bitmap.readPixels(pixels, startY = row, width = width, height = 1)
                pixels.forEachIndexed { column, pixel ->
                    // All print content is neutral, so a channel is its grayscale value.
                    gray[row * width + column] = (pixel shr 16 and 255).toByte()
                }
            }
            writer.addPage(width, height, gray)
            yield()
        }
        return writer.finish()
    }
}
