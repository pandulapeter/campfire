/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.notation

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Matrix
import com.pandulapeter.campfire.abc.api.AbcSystem
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Turns [NotationDrawing]s into what a canvas draws ([NotationSystemPaint]), each once: the outlines of a system merged
 * into a few paths, and its words laid out in [textStyle]'s typeface (the app's Inter), at the size abc2svg asked for.
 *
 * Words are laid out at the pixels to the SVG unit a drawing was rendered for, the size it is drawn at where its column
 * is as wide as its page, so that they are measured at the size they will mostly be seen at rather than scaled up from
 * a tiny one; a narrower column, or a pinch on its way to the next rendering, scales the whole drawing, words included.
 */
internal class NotationPainter(
    private val textMeasurer: TextMeasurer,
    private val density: Density,
    private val textStyle: TextStyle,
) {
    private val paints = HashMap<Pair<NotationDrawing, Float>, List<NotationSystemPaint>>()
    private val glyphPaths = HashMap<AbcMusicGlyph, Path>()
    private val pathParser = PathParser()

    /** The systems of [drawing], its words laid out at [unitSize] pixels to the unit, painted anew on every call. */
    fun paint(drawing: NotationDrawing, unitSize: Float): List<NotationSystemPaint> = drawing.systems.map { paint(it, unitSize) }

    /**
     * [paint], kept for the drawings asked for most recently: the PDF draws its pages one at a time and asks for each
     * system of a tune, and one tune can be printed at two scales (inside a chorus and outside it).
     */
    fun paintOf(drawing: NotationDrawing, unitSize: Float) = paints.getOrPut(drawing to unitSize) {
        if (paints.size >= MAX_PAINTED_DRAWINGS) paints.clear()
        paint(drawing, unitSize)
    }

    private fun paint(system: NotationSystem, unitSize: Float): NotationSystemPaint {
        val fills = Path()
        val strokes = mutableListOf<NotationStrokePaint>()
        system.shapes.forEach { shape ->
            val path = parse(shape.path).transformed(shape.transform)
            val stroke = shape.stroke
            if (stroke == null) {
                fills.addPath(path)
            } else {
                strokes += NotationStrokePaint(
                    path = path,
                    width = stroke.width * shape.transform.scale,
                    dashes = stroke.dashes?.map { it * shape.transform.scale },
                )
            }
        }
        system.glyphs.forEach { fills.addGlyph(it.glyph, it.x, it.y, it.size) }
        val texts = mutableListOf<NotationTextPaint>()
        system.texts.forEach { text ->
            // A lyric's font is larger than it is drawn, to leave room for the app's wider typeface (see AbcSystem).
            val layouts = text.runs.map { run ->
                (run as? NotationTextRun.Words)?.let { layout(it.text, if (it.role == NotationTextRole.LYRIC) it.font.scaled(1f / AbcSystem.LYRICS_ROOM) else it.font, unitSize) }
            }
            val widths = text.runs.mapIndexed { index, run ->
                when (run) {
                    is NotationTextRun.Words -> layouts[index]!!.size.width / unitSize
                    is NotationTextRun.Glyphs -> run.glyphs.sumOf { it.advance.toDouble() }.toFloat() * run.size / AbcMusicGlyph.UNITS_PER_EM
                }
            }
            var x = text.x - when (text.anchor) {
                NotationAnchor.START -> 0f
                NotationAnchor.MIDDLE -> widths.sum() / 2
                NotationAnchor.END -> widths.sum()
            }
            val firstWords = layouts.firstNotNullOfOrNull { it }
            if (text.isBoxed && firstWords != null) {
                // abc2svg's box is a CSS outline, 1 unit wide and 1 unit off the text, around the words as measured.
                val top = text.y - firstWords.firstBaseline / unitSize - BOX_GAP
                strokes += NotationStrokePaint(
                    path = Path().apply {
                        addRect(Rect(left = x - BOX_GAP, top = top, right = x + widths.sum() + BOX_GAP, bottom = top + firstWords.size.height / unitSize + 2 * BOX_GAP))
                    },
                    width = BOX_STROKE,
                    dashes = null,
                )
            }
            text.runs.forEachIndexed { index, run ->
                when (run) {
                    is NotationTextRun.Words -> texts += NotationTextPaint(
                        layout = layouts[index]!!,
                        x = x,
                        baseline = text.y + run.dy,
                        isChord = run.role == NotationTextRole.CHORD,
                    )

                    is NotationTextRun.Glyphs -> run.glyphs.fold(x) { glyphX, glyph ->
                        fills.addGlyph(glyph, glyphX, text.y + run.dy, run.size)
                        glyphX + glyph.advance * run.size / AbcMusicGlyph.UNITS_PER_EM
                    }
                }
                x += widths[index]
            }
        }
        return NotationSystemPaint(width = system.width, height = system.height, fills = fills, strokes = strokes, texts = texts)
    }

    private fun layout(text: String, font: NotationFont, unitSize: Float): TextLayoutResult = textMeasurer.measure(
        text = text,
        style = textStyle.copy(
            fontSize = font.size.toFontSize(unitSize),
            fontWeight = if (font.isBold) FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (font.isItalic) FontStyle.Italic else FontStyle.Normal,
            // abc2svg places a line by its baseline, which an inherited line height would move away from the glyphs.
            lineHeight = TextUnit.Unspecified,
        ),
        softWrap = false,
        maxLines = 1,
    )

    /** The size of [this] SVG units, laid out at [unitSize] pixels each, whatever the user's font scale says. */
    private fun Float.toFontSize(unitSize: Float) = (this * unitSize / density.density / density.fontScale).sp

    private companion object {
        const val MAX_PAINTED_DRAWINGS = 8
        const val BOX_STROKE = 1f

        /** The outline's offset plus half its stroke, which is drawn centred on the rectangle's edge. */
        const val BOX_GAP = 1.5f
    }

    private fun parse(pathData: String) = pathParser.parsePathString(pathData).toPath().also { pathParser.clear() }

    private fun Path.addGlyph(glyph: AbcMusicGlyph, x: Float, y: Float, size: Float) {
        val outline = glyphPaths.getOrPut(glyph) { parse(glyph.path) }
        addPath(Path().apply { addPath(outline) }.transformed(NotationTransform(x = x, y = y, scale = size / AbcMusicGlyph.UNITS_PER_EM)))
    }

    private fun Path.transformed(transform: NotationTransform) = apply {
        if (transform != NotationTransform.IDENTITY) {
            transform(
                Matrix().apply {
                    this[0, 0] = transform.scale
                    this[1, 1] = transform.scale
                    this[3, 0] = transform.x
                    this[3, 1] = transform.y
                },
            )
        }
    }
}

/** One system of a [NotationDrawing] ready to draw (see [drawNotationSystem]), in its SVG units. */
internal class NotationSystemPaint(
    val width: Float,
    val height: Float,
    val fills: Path,
    val strokes: List<NotationStrokePaint>,
    val texts: List<NotationTextPaint>,
)

internal class NotationStrokePaint(val path: Path, val width: Float, val dashes: List<Float>?)

/** Words laid out at the pixels to the unit their drawing was rendered for, their baseline's start at ([x], [baseline]) units. */
internal class NotationTextPaint(val layout: TextLayoutResult, val x: Float, val baseline: Float, val isChord: Boolean)
