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

/** What one class of abc2svg's `<style>` says, as far as the drawing reads it. */
internal data class NotationCssRule(
    val font: NotationFont?,
    val isStroke: Boolean,
    val strokeWidth: Float?,
    val dashes: List<Float>?,
)

/**
 * The single-class rules of a `<style>` (`.f3{font:bold 13.0px text,serif}`, `.sW{stroke:currentColor;…}`), by class.
 * Every other rule is left out: the `@font-face` that embeds the music font (drawn from [abcMusicGlyphs] instead), the
 * `white-space` of music text (which the drawing keeps as written anyway), and the box outline.
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
    )
}

/** `font: [bold] [italic] 13.0px family[,fallback]`, as abc2svg writes it. */
private fun parseFontShorthand(value: String): NotationFont? {
    val words = value.split(' ').filter { it.isNotEmpty() }
    val sizeIndex = words.indexOfFirst { it.endsWith("px") }.takeIf { it >= 0 } ?: return null
    val size = parseSvgLength(words[sizeIndex]) ?: return null
    val styles = words.subList(0, sizeIndex)
    val family = words.drop(sizeIndex + 1).joinToString(" ")
    return NotationFont(
        size = size,
        isBold = "bold" in styles,
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

private val SINGLE_CLASS_RULE = Regex("""\.([\w-]+)\s*\{([^}]*)}""")
private const val MUSIC_FAMILY = "music"
