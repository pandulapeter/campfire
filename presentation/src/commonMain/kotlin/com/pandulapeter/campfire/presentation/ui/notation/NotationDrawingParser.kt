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

import com.pandulapeter.campfire.abc.api.AbcSystem

/**
 * Reads the systems of one rendering into a [NotationDrawing], or returns null where any of them holds something the
 * drawing does not know (an element, a transform, a music symbol the font lacks): the caller then shows the tune as its
 * text, which is better than notation with something silently missing.
 *
 * The styles and the shared shapes (`<defs>`) are gathered across the systems in order, since abc2svg writes them once,
 * into the first system that needs them, and only within this one rendering, whose class numbers are its own. A system
 * that draws nothing (the header of a tune with its title switched off still carries the styles) is left out.
 */
internal fun parseNotationDrawing(systems: List<AbcSystem>): NotationDrawing? {
    val reader = NotationSystemReader()
    return NotationDrawing(
        systems.mapNotNull { system ->
            val root = parseSvgXml(system.svg)?.takeIf { it.name == "svg" } ?: return null
            reader.read(root) ?: return null
        }.filter { it.shapes.isNotEmpty() || it.glyphs.isNotEmpty() || it.texts.isNotEmpty() },
    )
}

/** What an element inherits from those around it: SVG's inheritance, as far as abc2svg relies on it. */
private data class NotationContext(
    val transform: NotationTransform,
    val font: NotationFont?,
    val strokeWidth: Float,
    val anchor: NotationAnchor,
)

private class NotationUnsupportedException : Exception()

private class NotationSystemReader {

    private val classes = HashMap<String, NotationCssRule>()
    private val definitions = HashMap<String, SvgElement>()
    private var shapes = mutableListOf<NotationShape>()
    private var glyphs = mutableListOf<NotationGlyph>()
    private var texts = mutableListOf<NotationText>()

    fun read(root: SvgElement): NotationSystem? = try {
        shapes = mutableListOf()
        glyphs = mutableListOf()
        texts = mutableListOf()
        // Read first wherever they stand, since an element may come before the <style> that names its class.
        root.elements.filter { it.name == "style" }.forEach { classes += parseNotationCss(it.text) }
        root.elements.filter { it.name == "defs" }.forEach { defs ->
            defs.elements.forEach { definition -> definition.attributes["id"]?.let { definitions[it] = definition } }
        }
        val context = contextOf(
            element = root,
            parent = NotationContext(NotationTransform.IDENTITY, font = null, strokeWidth = DEFAULT_STROKE_WIDTH, anchor = NotationAnchor.START),
        )
        root.elements.forEach { read(it, context) }
        NotationSystem(
            width = root.attributes["width"]?.let(::parseSvgLength) ?: throw NotationUnsupportedException(),
            height = root.attributes["height"]?.let(::parseSvgLength) ?: throw NotationUnsupportedException(),
            shapes = shapes,
            glyphs = glyphs,
            texts = texts,
        )
    } catch (_: NotationUnsupportedException) {
        null
    }

    private fun read(element: SvgElement, parent: NotationContext) {
        when (element.name) {
            "style", "defs" -> Unit
            "g" -> contextOf(element, parent).let { context -> element.elements.forEach { read(it, context) } }
            "use" -> {
                val id = (element.attributes["xlink:href"] ?: element.attributes["href"])?.removePrefix("#")
                val definition = definitions[id] ?: throw NotationUnsupportedException()
                val context = contextOf(element, parent)
                read(definition, context.copy(transform = context.transform.translated(element.number("x"), element.number("y"))))
            }

            "path" -> readPath(element, contextOf(element, parent))
            "text" -> readText(element, contextOf(element, parent))
            else -> throw NotationUnsupportedException()
        }
    }

    private fun contextOf(element: SvgElement, parent: NotationContext): NotationContext {
        var transform = parent.transform
        element.attributes["transform"]?.let { value ->
            TRANSFORM.findAll(value).forEach { match ->
                val numbers = parseSvgNumbers(match.groupValues[2]) ?: throw NotationUnsupportedException()
                transform = when (match.groupValues[1]) {
                    "translate" -> transform.translated(numbers.getOrElse(0) { 0f }, numbers.getOrElse(1) { 0f })
                    // A scale that differs between the axes would distort the glyphs and the strokes; abc2svg never writes one.
                    "scale" -> if (numbers.size == 1 || numbers.size == 2 && numbers[0] == numbers[1]) {
                        transform.scaled(numbers[0])
                    } else {
                        throw NotationUnsupportedException()
                    }

                    else -> throw NotationUnsupportedException()
                }
            }
            if (value.replace(TRANSFORM, "").isNotBlank()) throw NotationUnsupportedException()
        }
        return NotationContext(
            transform = transform,
            font = classesOf(element).firstNotNullOfOrNull { it.font } ?: parent.font,
            strokeWidth = element.attributes["stroke-width"]?.let(::parseSvgLength) ?: parent.strokeWidth,
            anchor = when (element.attributes["text-anchor"]) {
                null -> parent.anchor
                "middle" -> NotationAnchor.MIDDLE
                "end" -> NotationAnchor.END
                else -> NotationAnchor.START
            },
        )
    }

    private fun classesOf(element: SvgElement) = element.classNames.mapNotNull { classes[it] }

    private fun readPath(element: SvgElement, context: NotationContext) {
        val rule = classesOf(element).firstOrNull { it.isStroke }
        shapes += NotationShape(
            path = element.attributes["d"] ?: throw NotationUnsupportedException(),
            transform = context.transform,
            stroke = rule?.let {
                NotationStroke(
                    // An element's own stroke-width beats its class's, which beats the one inherited from around it.
                    width = element.attributes["stroke-width"]?.let(::parseSvgLength) ?: rule.strokeWidth ?: context.strokeWidth,
                    dashes = element.attributes["stroke-dasharray"]?.let(::parseSvgNumbers) ?: rule.dashes,
                )
            },
        )
    }

    private fun readText(element: SvgElement, context: NotationContext) {
        val font = context.font ?: throw NotationUnsupportedException()
        val role = when {
            AbcSystem.CHORD_SYMBOL_CLASS in element.classNames -> NotationTextRole.CHORD
            AbcSystem.LYRICS_CLASS in element.classNames -> NotationTextRole.LYRIC
            else -> NotationTextRole.OTHER
        }
        val xs = element.attributes["x"]?.let { parseSvgNumbers(it) ?: throw NotationUnsupportedException() }.orEmpty()
        val ys = element.attributes["y"]?.let { parseSvgNumbers(it) ?: throw NotationUnsupportedException() }.orEmpty()
        val transform = context.transform
        if (font.isMusic && element.children.none { it is SvgElement }) {
            // A line of music symbols, each placed by its own coordinate pair where the lists have one.
            var x = xs.firstOrNull() ?: 0f
            var y = ys.firstOrNull() ?: 0f
            element.text.textContent().codePointList().forEachIndexed { index, codePoint ->
                xs.getOrNull(index)?.let { x = it }
                ys.getOrNull(index)?.let { y = it }
                if (codePoint == ' '.code) return@forEachIndexed
                val glyph = abcMusicGlyphs[codePoint] ?: throw NotationUnsupportedException()
                val offset = when (context.anchor) {
                    NotationAnchor.START -> 0f
                    NotationAnchor.MIDDLE -> glyph.advance * font.size / AbcMusicGlyph.UNITS_PER_EM / 2
                    NotationAnchor.END -> glyph.advance * font.size / AbcMusicGlyph.UNITS_PER_EM
                }
                glyphs += NotationGlyph(glyph = glyph, x = transform.mapX(x - offset), y = transform.mapY(y), size = font.size * transform.scale)
                x += glyph.advance * font.size / AbcMusicGlyph.UNITS_PER_EM
            }
            return
        }
        if (xs.size > 1 || ys.size > 1) throw NotationUnsupportedException()
        val runs = mutableListOf<NotationTextRun>()
        var dy = 0f
        element.children.forEach { child ->
            val (text, runFont) = when (child) {
                is SvgText -> child.text to font
                is SvgElement -> {
                    if (child.name != "tspan" || child.children.any { it is SvgElement }) throw NotationUnsupportedException()
                    child.attributes["dy"]?.let { dy += parseSvgLength(it) ?: throw NotationUnsupportedException() }
                    val classFont = classesOf(child).firstNotNullOfOrNull { it.font } ?: font
                    val size = child.attributes["style"]?.let { FONT_SIZE.find(it)?.groupValues?.get(1)?.toFloatOrNull() }
                    child.text to (size?.let { classFont.copy(size = it) } ?: classFont)
                }
            }
            val content = text.textContent()
            if (content.isEmpty()) return@forEach
            runs += if (runFont.isMusic) {
                NotationTextRun.Glyphs(
                    glyphs = content.codePointList().filter { it != ' '.code }.map { abcMusicGlyphs[it] ?: throw NotationUnsupportedException() },
                    size = runFont.size * transform.scale,
                    dy = dy * transform.scale,
                )
            } else {
                NotationTextRun.Words(text = content, font = runFont.scaled(transform.scale), role = role, dy = dy * transform.scale)
            }
        }
        if (runs.isNotEmpty()) {
            texts += NotationText(x = transform.mapX(xs.firstOrNull() ?: 0f), y = transform.mapY(ys.firstOrNull() ?: 0f), anchor = context.anchor, runs = runs)
        }
    }

    private companion object {
        /** SVG's initial stroke width, which abc2svg's root element replaces with its own. */
        const val DEFAULT_STROKE_WIDTH = 1f
        val TRANSFORM = Regex("""(translate|scale|rotate|matrix|skewX|skewY)\s*\(([^)]*)\)""")
        val FONT_SIZE = Regex("""font-size:\s*([\d.]+)px""")
    }
}

private val SvgElement.classNames get() = attributes["class"]?.split(' ')?.filter { it.isNotEmpty() }.orEmpty()

private fun SvgElement.number(attribute: String) = attributes[attribute]?.let(::parseSvgLength) ?: 0f

/**
 * Character data as abc2svg means it: its line breaks fall between attributes and content rather than in the words,
 * and its spaces are kept as written (its music text is `white-space: pre`, and a tempo's spaces separate its pieces).
 */
private fun String.textContent() = replace("\n", "").replace("\r", "")

/** The Unicode code points of the text, the music font's private use characters among them. */
private fun String.codePointList(): List<Int> {
    val codePoints = ArrayList<Int>(length)
    var index = 0
    while (index < length) {
        val high = this[index]
        if (high.isHighSurrogate() && index + 1 < length && this[index + 1].isLowSurrogate()) {
            codePoints += ((high.code - 0xD800) shl 10) + (this[index + 1].code - 0xDC00) + 0x10000
            index += 2
        } else {
            codePoints += high.code
            index++
        }
    }
    return codePoints
}
