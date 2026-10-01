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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.yield
import kotlin.coroutines.coroutineContext
import kotlin.math.ceil

/**
 * Draws and measures the texts of a [PrintDocument]. [textFontFamily] is the face the viewer sets lyrics in, and
 * [monospaceFontFamily] the one of its tablature and grids, so that the page reads like the screen.
 */
internal class PrintRenderer(
    private val measurer: TextMeasurer,
    private val monospaceFontFamily: FontFamily = FontFamily.Monospace,
    private val textFontFamily: FontFamily = FontFamily.Default,
) {
    private val cache = mutableMapOf<Pair<String, PrintStyle>, TextLayoutResult>()

    private fun PrintStyle.toTextStyle() = TextStyle(
        color = Color(gray, gray, gray),
        fontFamily = if (monospace) monospaceFontFamily else textFontFamily,
        fontSize = size.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
        fontFeatureSettings = "liga=0",
    )

    private fun text(value: String, style: PrintStyle): TextLayoutResult {
        if (cache.size >= 512) cache.clear()
        return cache.getOrPut(value to style) {
            measurer.measure(
                text = value,
                style = style.toTextStyle(),
                softWrap = false,
                density = Density(1f, 1f),
                layoutDirection = LayoutDirection.Ltr,
            )
        }
    }

    fun width(value: String, style: PrintStyle): Float {
        // Measuring intermediate substrings must not keep all of them alive for the life of a large setlist.
        return measurer.measure(value, style.toTextStyle(), softWrap = false, density = Density(1f, 1f), layoutDirection = LayoutDirection.Ltr).size.width.toFloat()
    }

    fun draw(scope: DrawScope, page: PrintPage, scale: Float) = with(scope) {
        drawRect(Color.White)
        scale(scale, scale, pivot = Offset.Zero) {
            page.texts.forEach { item -> drawText(text(item.text, item.style), topLeft = Offset(item.x, item.y)) }
        }
    }

    suspend fun pdf(document: PrintDocument, title: String): ByteArray {
        val writer = PrintPdfWriter(document.width, document.height, title)
        val scale = 3f // 216 dpi: text remains sharp at its physical print size.
        val width = ceil(document.width * scale).toInt()
        val height = ceil(document.height * scale).toInt()
        // A page is an 18 MB native bitmap whose memory Skia frees only when its wrapper happens to be collected, and wasm
        // memory never shrinks, so one bitmap and one set of buffers serve every page: draw paints the whole page white
        // before anything else, and addPage has compressed the packed page into its own output by the time it returns.
        val bitmap = ImageBitmap(width, height)
        val canvas = Canvas(bitmap)
        val drawScope = CanvasDrawScope()
        val pixels = IntArray(width * PDF_BAND_ROWS)
        val packed = ByteArray(printRowBytes(width) * height)
        document.pages.forEach { page ->
            coroutineContext.ensureActive()
            drawScope.draw(Density(1f), LayoutDirection.Ltr, canvas, Size(width.toFloat(), height.toFloat())) {
                draw(this, page, scale)
            }
            for (top in 0 until height step PDF_BAND_ROWS) {
                coroutineContext.ensureActive()
                yield()
                val rows = minOf(PDF_BAND_ROWS, height - top)
                bitmap.readPixels(pixels, startY = top, width = width, height = rows)
                packPrintRows(pixels, width, rows, packed, firstRow = top)
            }
            writer.addPage(width, height, packed)
            yield()
        }
        return writer.finish()
    }
}

private const val PDF_BAND_ROWS = 64

/**
 * The luminance of an ARGB pixel by the Rec. 601 weights. Most print content is black on white, but colour emoji in a
 * title or a lyric are drawn in colour, and a single channel would turn a red flame white.
 */
internal fun printGray(argb: Int): Int {
    val red = argb shr 16 and 255
    val green = argb shr 8 and 255
    val blue = argb and 255
    return (299 * red + 587 * green + 114 * blue) / 1000
}
