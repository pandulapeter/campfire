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
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.pandulapeter.campfire.chordpro.language.WritingDirection
import com.pandulapeter.campfire.chordpro.model.ChordProLine.Lyrics.Formatting
import com.pandulapeter.campfire.presentation.ui.chords.drawChordDiagram
import com.pandulapeter.campfire.presentation.ui.notation.NotationPainter
import com.pandulapeter.campfire.presentation.ui.notation.drawNotationSystem
import com.pandulapeter.campfire.presentation.ui.songLayout.PADDING
import com.pandulapeter.campfire.presentation.ui.songLayout.PADDING_MARK
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
    /** The engraved tunes' outlines and words, laid out in [textFontFamily] in PDF points as the texts are. */
    private val notationPainter = NotationPainter(
        textMeasurer = measurer,
        density = Density(1f, 1f),
        textStyle = TextStyle(fontFamily = textFontFamily, fontFeatureSettings = "liga=0"),
    )

    /** The laid out texts of the pages drawn last, one for each of a page's [PrintPage.texts]. */
    private val layouts = PrintPageCache { page -> page.texts.map { text(it.text, it.style, it.formatting) } }

    private fun PrintStyle.toTextStyle() = TextStyle(
        color = Color(gray, gray, gray),
        fontFamily = if (monospace) monospaceFontFamily else textFontFamily,
        fontSize = size.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
        fontFeatureSettings = if (tabular) "liga=0, tnum" else "liga=0",
        textDirection = when (direction) {
            WritingDirection.LEFT_TO_RIGHT -> TextDirection.Ltr
            WritingDirection.RIGHT_TO_LEFT -> TextDirection.Rtl
            null -> TextDirection.Unspecified
        },
    )

    private fun text(value: String, style: PrintStyle, formatting: List<Formatting> = emptyList()) = measurer.measure(
        text = if (formatting.isEmpty()) AnnotatedString(value) else value.withSpans(formatting),
        style = style.toTextStyle(),
        softWrap = false,
        density = Density(1f, 1f),
        // A text of no direction of its own (the running order's) is read by its first strong letter, else left to right.
        layoutDirection = if (style.isRightToLeft) LayoutDirection.Rtl else LayoutDirection.Ltr,
    )

    /**
     * The width of [value] in PDF points, which the layout measures by. Not cached: the wrapping measures many
     * intermediate substrings, and keeping all of them alive for the life of a large setlist would cost more than it saves.
     */
    fun width(value: String, style: PrintStyle): Float = text(value, style).size.width.toFloat()

    /**
     * A lyric row laid out as it is drawn, and where the chords at [offsets] hang from, found as the screen's
     * `SongLineWithChords` finds them: where the run a chord lands in reads the paragraph's way, the caret position is
     * its character's starting edge; where it reads the other way, the caret at the run's boundary stands with the run
     * before, so the character's own box says where it starts.
     */
    fun lyricLayout(value: String, style: PrintStyle, formatting: List<Formatting>, offsets: List<Int>): PrintLyricLayout {
        val layout = text(value, style, formatting)
        val isLineRightToLeft = style.isRightToLeft
        return PrintLyricLayout(
            width = layout.size.width.toFloat(),
            anchors = offsets.map { requested ->
                val offset = requested.coerceIn(0, value.length)
                val isRunRightToLeft = if (offset < value.length) layout.getBidiRunDirection(offset) == ResolvedTextDirection.Rtl else isLineRightToLeft
                val x = if (isRunRightToLeft == isLineRightToLeft) {
                    layout.getHorizontalPosition(offset, usePrimaryDirection = true)
                } else {
                    layout.getBoundingBox(offset).let { if (isRunRightToLeft) it.right else it.left }
                }
                PrintChordAnchor(x, isRunRightToLeft)
            },
        )
    }

    /** Draws [page] on white, [scale] being the size of a PDF point in the pixels of [scope]. */
    fun draw(scope: DrawScope, page: PrintPage, scale: Float) = with(scope) {
        drawRect(Color.White)
        scale(scale, scale, pivot = Offset.Zero) {
            page.rules.forEach { rule ->
                drawRect(
                    color = Color(rule.gray, rule.gray, rule.gray),
                    topLeft = Offset(rule.x, rule.y),
                    size = Size(rule.width, rule.height),
                )
            }
            page.texts.zip(layouts.of(page)) { item, layout -> if (item.isDrawn) drawText(layout, topLeft = Offset(item.x, item.y)) }
            page.diagrams.forEach { diagram ->
                translate(diagram.x, diagram.y) {
                    drawChordDiagram(
                        geometry = diagram.geometry,
                        area = Size(diagram.width, diagram.height),
                        lineColor = Color.Black,
                        mutedColor = DIAGRAM_MUTED_COLOR,
                        rootColor = DIAGRAM_ROOT_COLOR,
                        backgroundColor = Color.White,
                        textMeasurer = measurer,
                        fontFamily = textFontFamily,
                        // One pixel of the page image rather than one point, which would be three of them.
                        minimumStroke = 1f / scale,
                    )
                }
            }
            page.notations.forEach { notation ->
                translate(notation.x, notation.y) {
                    drawNotationSystem(
                        system = notationPainter.paintOf(notation.drawing, unitSize = notation.scale)[notation.system],
                        top = 0f,
                        scale = notation.scale,
                        unitSize = notation.scale,
                        color = Color.Black,
                        chordColor = Color.Black,
                    )
                }
            }
        }
    }

    /** The same shaping as drawText, including fallback glyphs, surrogate pairs and combining characters. */
    internal suspend fun selectableText(page: PrintPage): List<PrintPdfText> = buildList {
        var characters = 0
        // The page was just drawn, so its layouts are the ones drawn.
        val pageLayouts = layouts.of(page)
        for ((run, item) in page.texts.withIndex()) {
            if (!item.isSelectable) continue
            val layout = pageLayouts[run]
            val padding = item.text.paddingRuns()
            var offset = 0
            while (offset < item.text.length) {
                if (++characters % 256 == 0) yield()
                val start = offset
                val box = layout.getBoundingBox(offset)
                fun next() {
                    offset += if (item.text[offset].isHighSurrogate() && item.text.getOrNull(offset + 1)?.isLowSurrogate() == true) 2 else 1
                }
                next()
                // A shaped cluster shares a rectangle. Keep it as one Unicode mapping rather than overlaying copies.
                while (offset < item.text.length && offset - start < 62) {
                    val following = layout.getBoundingBox(offset)
                    if (following != box && following.width != 0f) break
                    next()
                }
                // The chord layout adds invisible wrap opportunities and marks, the direction marks closing in a piece
                // read against its line among them, which are not song content, as the isolates a sentence puts around
                // the texts it names are not, and makes the spaces under a chord non-breaking: those are written as the
                // spaces they are, and only the padding as the no-break spaces it is, which the import leaves out of the
                // words it reads back.
                val value = buildString {
                    for (index in start until offset) when (val character = item.text[index]) {
                        '\u200B', '\u200E', '\u200F', '\u2066', '\u2067', '\u2068', '\u2069', PADDING_MARK -> Unit
                        PADDING -> append(if (padding[index]) PADDING else ' ')
                        else -> append(character)
                    }
                }
                if (value.isNotEmpty()) add(PrintPdfText(value, item.x + box.left, item.y + box.top,
                    box.width.coerceAtLeast(0.001f), box.height.coerceAtLeast(0.001f), item.style, run,
                    isRtl = layout.getBidiRunDirection(start) == ResolvedTextDirection.Rtl))
            }
        }
    }

    /**
     * The PDF file of [document], each page drawn as an image with matching invisible, selectable text.
     *
     * @param onPage Called with the number of pages done after each page is added to the file.
     */
    suspend fun pdf(document: PrintDocument, title: String, onPage: (done: Int) -> Unit = {}): ByteArray {
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
        document.pages.forEachIndexed { index, page ->
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
            writer.addPage(width, height, packed, selectableText(page))
            onPage(index + 1)
            yield()
        }
        return writer.finish()
    }
}

private fun String.withSpans(formatting: List<Formatting>) = buildAnnotatedString {
    append(this@withSpans)
    formatting.forEach { range ->
        val span = when (range.style) {
            Formatting.Style.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
            Formatting.Style.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
            Formatting.Style.UNDERLINE -> SpanStyle(textDecoration = TextDecoration.Underline)
        }
        addStyle(span, range.start, range.end)
    }
}

// The page is gray, so the root a diagram draws in the accent on screen is a mid gray here, darker than the paler shade a
// keyboard's other pressed keys are mixed from it and white, and lighter than the black of every other dot.
private val DIAGRAM_ROOT_COLOR = Color(110, 110, 110)
private val DIAGRAM_MUTED_COLOR = Color(90, 90, 90)

/** The rows of a page read out of the bitmap at a time, which is as often as an export checks whether it was cancelled. */
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

/** Which characters of a padded lyric are padding: the no-break spaces after a [PADDING_MARK]. */
private fun String.paddingRuns() = BooleanArray(length).also { runs ->
    var isPadding = false
    forEachIndexed { index, character ->
        isPadding = character == PADDING_MARK || character == PADDING && isPadding
        runs[index] = character == PADDING && isPadding
    }
}
