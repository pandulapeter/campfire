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

    /**
     * The systems of [drawing], its words laid out at [unitSize] pixels to the unit, painted anew on every call. A word
     * repeated in one font (a fret, a chord name, a bar number) is laid out once and drawn from that layout wherever it
     * stands; the layouts are shared within the call only, so a long-lived painter holds none of them afterwards.
     */
    fun paint(drawing: NotationDrawing, unitSize: Float): List<NotationSystemPaint> {
        val layouts = HashMap<Pair<String, NotationFont>, TextLayoutResult>()
        return drawing.systems.map { paint(it, unitSize, layouts) }
    }

    /**
     * [paint], kept for the drawings asked for most recently: the PDF draws its pages one at a time and asks for each
     * system of a tune, and one tune can be printed at two scales (inside a chorus and outside it).
     */
    fun paintOf(drawing: NotationDrawing, unitSize: Float) = paints.getOrPut(drawing to unitSize) {
        if (paints.size >= MAX_PAINTED_DRAWINGS) paints.clear()
        paint(drawing, unitSize)
    }

    private fun paint(system: NotationSystem, unitSize: Float, layouts: MutableMap<Pair<String, NotationFont>, TextLayoutResult>): NotationSystemPaint {
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
        system.glyphs.forEach { fills.addGlyph(it.glyph, it.transform) }
        val texts = mutableListOf<NotationTextPaint>()
        val knockouts = Path()
        system.texts.forEach { text ->
            // A lyric's font is larger than it is drawn, to leave room for the app's wider typeface (see AbcSystem).
            val runLayouts = text.runs.map { run ->
                (run as? NotationTextRun.Words)?.let {
                    val font = if (it.role == NotationTextRole.LYRIC) it.font.scaled(1f / AbcSystem.LYRICS_ROOM) else it.font
                    layouts.getOrPut(it.text to font) { layout(it.text, font, unitSize) }
                }
            }
            val widths = text.runs.mapIndexed { index, run ->
                when (run) {
                    is NotationTextRun.Words -> runLayouts[index]!!.size.width / unitSize
                    is NotationTextRun.Glyphs -> run.glyphs.sumOf { it.advance.toDouble() }.toFloat() * run.size / AbcMusicGlyph.UNITS_PER_EM
                }
            }
            var x = text.x - when (text.anchor) {
                NotationAnchor.START -> 0f
                NotationAnchor.MIDDLE -> widths.sum() / 2
                NotationAnchor.END -> widths.sum()
            }
            val firstWords = runLayouts.firstNotNullOfOrNull { it }
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
            if (text.isKnockedOut && firstWords != null) {
                // The region of abc2svg's flood filter: the words' box as measured, a tenth wider on each side and a tenth
                // shorter at the top and at the bottom.
                val top = text.y - firstWords.firstBaseline / unitSize
                val height = firstWords.size.height / unitSize
                knockouts.addRect(
                    Rect(
                        left = x - KNOCKOUT_MARGIN * widths.sum(),
                        top = top + KNOCKOUT_MARGIN * height,
                        right = x + (1 + KNOCKOUT_MARGIN) * widths.sum(),
                        bottom = top + (1 - KNOCKOUT_MARGIN) * height,
                    ),
                )
            }
            text.runs.forEachIndexed { index, run ->
                when (run) {
                    is NotationTextRun.Words -> texts += NotationTextPaint(
                        layout = runLayouts[index]!!,
                        x = x,
                        baseline = text.y + run.dy,
                        isChord = run.role == NotationTextRole.CHORD,
                    )

                    is NotationTextRun.Glyphs -> run.glyphs.fold(x) { glyphX, glyph ->
                        fills.addGlyph(glyph, NotationTransform(x = glyphX, y = text.y + run.dy).scaled(run.size / AbcMusicGlyph.UNITS_PER_EM))
                        glyphX + glyph.advance * run.size / AbcMusicGlyph.UNITS_PER_EM
                    }
                }
                x += widths[index]
            }
        }
        return NotationSystemPaint(width = system.width, height = system.height, fills = fills, strokes = strokes, knockouts = knockouts, texts = texts)
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

    /**
     * The size of [this] SVG units, laid out at [unitSize] pixels each: the unit already holds the system's font size, so
     * the pixels are converted back through the density's own font scaling, which Android's is not proportional.
     */
    private fun Float.toFontSize(unitSize: Float) = with(density) { (this@toFontSize * unitSize).toSp() }

    private companion object {
        const val MAX_PAINTED_DRAWINGS = 8
        const val BOX_STROKE = 1f

        /** The outline's offset plus half its stroke, which is drawn centred on the rectangle's edge. */
        const val BOX_GAP = 1.5f

        /** The part of a knocked out text's width added on each side, and of its height taken off at the top and bottom. */
        const val KNOCKOUT_MARGIN = 0.1f
    }

    private fun parse(pathData: String) = pathParser.parsePathString(pathData).toPath().also { pathParser.clear() }

    private fun Path.addGlyph(glyph: AbcMusicGlyph, transform: NotationTransform) {
        val outline = glyphPaths.getOrPut(glyph) { parse(glyph.path) }
        addPath(Path().apply { addPath(outline) }.transformed(transform))
    }

    private fun Path.transformed(transform: NotationTransform) = apply {
        if (transform != NotationTransform.IDENTITY) {
            transform(
                Matrix().apply {
                    this[0, 0] = transform.a
                    this[0, 1] = transform.b
                    this[1, 0] = transform.c
                    this[1, 1] = transform.d
                    this[3, 0] = transform.x
                    this[3, 1] = transform.y
                },
            )
        }
    }
}

/**
 * One system of a [NotationDrawing] ready to draw (see [drawNotationSystem]), in its SVG units: [knockouts] are the boxes
 * filled in the page's colour between the lines and the words, behind the texts abc2svg floods the background of.
 */
internal class NotationSystemPaint(
    val width: Float,
    val height: Float,
    val fills: Path,
    val strokes: List<NotationStrokePaint>,
    val knockouts: Path,
    val texts: List<NotationTextPaint>,
)

internal class NotationStrokePaint(val path: Path, val width: Float, val dashes: List<Float>?)

/** Words laid out at the pixels to the unit their drawing was rendered for, their baseline's start at ([x], [baseline]) units. */
internal class NotationTextPaint(val layout: TextLayoutResult, val x: Float, val baseline: Float, val isChord: Boolean)
