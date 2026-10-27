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

/**
 * What one class of abc2svg's `<style>` says, as far as the drawing reads it: a font, a stroke, and what its modules
 * add (a text's anchor, a size relative to the text around it and a raised baseline for a jazz chord's superscript, an
 * outline for a boxed chord or part).
 */
internal data class NotationCssRule(
    val font: NotationFont?,
    val isStroke: Boolean,
    val strokeWidth: Float?,
    val dashes: List<Float>?,
    val anchor: NotationAnchor? = null,
    val relativeSize: Float? = null,
    val baselineShift: Float? = null,
    val isOutlined: Boolean = false,
)

/**
 * The single-class rules of a `<style>` (`.f3{font:bold 13.0px text,serif}`, `.sW{stroke:currentColor;…}`), by class.
 * Every other rule is left out: the `@font-face` that embeds the music font (drawn from [abcMusicGlyphs] instead) and
 * the `white-space` of music text (which the drawing keeps as written anyway).
 */
internal fun parseNotationCss(css: String): Map<String, NotationCssRule> = SINGLE_CLASS_RULE.findAll(css).associate { match ->
    val declarations = match.groupValues[2].split(';').mapNotNull { declaration ->
        declaration.split(':', limit = 2).takeIf { it.size == 2 }?.let { (name, value) -> name.trim() to value.trim() }
    }.toMap()
    match.groupValues[1] to NotationCssRule(
        font = declarations["font"]?.let(::parseFontShorthand),
        isStroke = "stroke" in declarations,
        strokeWidth = declarations["stroke-width"]?.let(::parseSvgLength),
        dashes = declarations["stroke-dasharray"]?.let(::parseSvgNumbers),
        anchor = declarations["text-anchor"]?.let(::parseTextAnchor),
        relativeSize = declarations["font-size"]?.let(::parsePercentage),
        baselineShift = declarations["baseline-shift"]?.let(::parsePercentage),
        isOutlined = declarations["outline"]?.let { it != "none" } == true,
    )
}

/** SVG's `text-anchor`, null for a value the drawing does not know. */
internal fun parseTextAnchor(value: String) = when (value.trim()) {
    "start" -> NotationAnchor.START
    "middle" -> NotationAnchor.MIDDLE
    "end" -> NotationAnchor.END
    else -> null
}

/** `75%` as 0.75; any other value (an absolute size) is not a proportion and reads as null. */
private fun parsePercentage(value: String) = value.trim().takeIf { it.endsWith('%') }?.removeSuffix("%")?.toFloatOrNull()?.div(100f)

/**
 * `font: [weight] [italic] 13.0px family[,fallback]`, as abc2svg writes it: the weight a word (`bold`) or a number (`700`, for
 * a font named `-Bold`), read as bold from semibold up.
 */
private fun parseFontShorthand(value: String): NotationFont? {
    val words = value.split(' ').filter { it.isNotEmpty() }
    val sizeIndex = words.indexOfFirst { it.endsWith("px") }.takeIf { it >= 0 } ?: return null
    val size = parseSvgLength(words[sizeIndex]) ?: return null
    val styles = words.subList(0, sizeIndex)
    val family = words.drop(sizeIndex + 1).joinToString(" ")
    return NotationFont(
        size = size,
        isBold = styles.any { it == "bold" || (it.toIntOrNull() ?: 0) >= BOLD_WEIGHT },
        isItalic = "italic" in styles || "oblique" in styles,
        isMusic = family.substringBefore(',').trim() == MUSIC_FAMILY,
    )
}

/** A length with or without its `px`, which is the only unit abc2svg writes. */
internal fun parseSvgLength(value: String) = value.trim().removeSuffix("px").toFloatOrNull()

/** A list of numbers separated by commas or spaces (`x="12.0,4.5"`, `5,10`), or null where one is not a number. */
internal fun parseSvgNumbers(value: String): List<Float>? = value.split(',', ' ', '\n', '\t').filter { it.isNotBlank() }.map {
    parseSvgLength(it) ?: return null
}

/** The closing brace is escaped because Android's ICU engine, unlike the JVM's, rejects a bare `}` outside a class. */
private val SINGLE_CLASS_RULE = Regex("""\.([\w-]+)\s*\{([^}]*)\}""")
private const val MUSIC_FAMILY = "music"

/** CSS semibold, the lightest weight drawn bold. */
private const val BOLD_WEIGHT = 600
