/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.syntax

import com.pandulapeter.campfire.chordpro.model.ChordProBlock
import com.pandulapeter.campfire.chordpro.model.ImageAlignment
import com.pandulapeter.campfire.chordpro.model.ImageLength
import com.pandulapeter.campfire.chordpro.syntax.ChordProImageDirectives.ID
import com.pandulapeter.campfire.chordpro.syntax.ChordProImageDirectives.SRC

/**
 * Reads the `{image}` directives of one song in order, remembering the assets they define.
 *
 * ChordPro says an asset definition holds an `id` and a `src` and nothing else, and that it only loads the picture:
 * a later `{image: id=…}` draws it. So a directive with those two attributes alone draws nothing, and one that adds
 * any other attribute to them both defines the asset and draws the picture. A later definition of the same `id`
 * replaces the earlier one for the directives after it.
 */
internal class ImageBlocks {

    private val assets = mutableMapOf<String, String>()

    /** The picture the directive with [value] draws, or null where it draws none. */
    fun read(value: String?): ChordProBlock.Image? {
        val attributes = ChordProImageDirectives.attributes(value.orEmpty())
        val map = ChordProImageDirectives.attributeMap(attributes)
        val source = sourceOf(attributes)
        val id = idOf(map)
        if (id != null && source != null) {
            assets[id] = source
            if (map.keys.all { it == ID || it == SRC }) return null
        }
        return ChordProBlock.Image(
            src = source ?: id?.let(assets::get) ?: return null,
            width = map[WIDTH]?.let(::length),
            height = map[HEIGHT]?.let(::length),
            scale = map[SCALE]?.let(::scale),
            alignment = alignment(map),
            title = map[TITLE]?.takeIf { it.isNotBlank() },
            attributes = map,
        )
    }

    private fun length(written: String): ImageLength? {
        val trimmed = written.trim()
        return if (trimmed.endsWith(PERCENT)) {
            positiveNumber(trimmed.dropLast(1))?.let(ImageLength::Percent)
        } else {
            positiveNumber(trimmed.removeSuffix(POINTS))?.let(ImageLength::Points)
        }
    }

    /** A factor, `50%` read as 0.5. Two factors (`0.5,0.8`) would stretch the picture, which is not followed. */
    private fun scale(written: String): Double? {
        val trimmed = written.trim()
        return if (trimmed.endsWith(PERCENT)) positiveNumber(trimmed.dropLast(1))?.div(100) else positiveNumber(trimmed)
    }

    /** `align` first; the deprecated `center` aligns to the left only where it is written as off. */
    private fun alignment(map: Map<String, String>): ImageAlignment? {
        map[ALIGN]?.trim()?.lowercase()?.let { written -> ImageAlignment.entries.firstOrNull { it.name.lowercase() == written } }?.let { return it }
        val center = map[CENTER]?.trim()?.lowercase() ?: return null
        return if (center == "0" || center == "false") ImageAlignment.LEFT else ImageAlignment.CENTER
    }

    /**
     * A number written in digits with at most one decimal point, and above zero. Not `toDoubleOrNull`, which also
     * reads `NaN`, `1e9`, and a `d` suffix, none of which a size is.
     */
    private fun positiveNumber(written: String): Double? {
        val text = written.trim()
        if (text.isEmpty() || text.count { it == '.' } > 1 || text.any { it != '.' && it !in '0'..'9' } || text.none { it in '0'..'9' }) return null
        return text.toDoubleOrNull()?.takeIf { it > 0 }
    }

    companion object {
        private const val WIDTH = "width"
        private const val HEIGHT = "height"
        private const val SCALE = "scale"
        private const val ALIGN = "align"
        private const val CENTER = "center"
        private const val TITLE = "title"
        private const val PERCENT = "%"
        private const val POINTS = "pt"

        /**
         * Whether a directive with [value] can draw or define a picture on its own line: it names a source, or an id that an
         * asset definition elsewhere in the song may give one. One naming neither is read past by [read] wherever it stands.
         */
        fun namesPicture(value: String?): Boolean {
            val attributes = ChordProImageDirectives.attributes(value.orEmpty())
            return sourceOf(attributes) != null || idOf(ChordProImageDirectives.attributeMap(attributes)) != null
        }

        private fun sourceOf(attributes: List<ChordProImageDirectives.Attribute>) =
            ChordProImageDirectives.sources(attributes).firstOrNull()?.value?.takeIf { it.isNotEmpty() }

        private fun idOf(map: Map<String, String>) = map[ID]?.takeIf { it.isNotEmpty() }
    }
}
