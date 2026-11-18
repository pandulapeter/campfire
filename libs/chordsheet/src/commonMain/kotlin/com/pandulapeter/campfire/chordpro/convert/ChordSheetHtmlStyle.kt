/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.convert

/**
 * The few properties of an element's inline `style` that say how a copied fragment is laid out: a browser writes the
 * computed ones it needs into the copy (Chrome and Safari do, Firefox writes none), so a page's own style sheet is not
 * needed to read them. Lengths are in pixels, an `em` taken as the 16 pixels of a browser's default font size, which is
 * exact enough to tell a gap from none.
 */
internal class ChordSheetHtmlStyle private constructor(private val properties: Map<String, String>) {

    /** Laid out as one box on its line, whatever blocks are inside it: `inline-block`, `inline-flex`, or `inline-grid`. */
    val isInlineBox get() = properties["display"]?.substringBefore('!')?.trim() in INLINE_BOXES

    /** Drawn as nothing, though it may take room: what it holds is not on the page as a reader sees it. */
    val isHidden
        get() = properties["display"]?.trim() == "none" ||
            properties["visibility"]?.trim().let { it == "hidden" || it == "collapse" } ||
            properties["opacity"]?.trim()?.toDoubleOrNull() == 0.0

    /** Its place among the items of a flex container, which lays them out by it before their order in the document. */
    val order get() = properties["order"]?.trim()?.toIntOrNull() ?: 0

    val marginLeft get() = properties["margin-left"]?.let(::pixels) ?: margins()?.let { it.getOrNull(3) ?: it.getOrNull(1) ?: it.first() }

    val marginRight get() = properties["margin-right"]?.let(::pixels) ?: margins()?.let { it.getOrNull(1) ?: it.first() }

    /** The `margin` shorthand's lengths in its own order (top, right, bottom, left), each missing one taken from another. */
    private fun margins() = properties["margin"]?.trim()?.split(WHITESPACE)?.map { pixels(it) ?: 0.0 }?.takeIf { it.size in 1..4 }

    private fun pixels(value: String): Double? {
        val match = LENGTH.matchEntire(value.substringBefore('!').trim()) ?: return null
        val number = match.groupValues[1].toDoubleOrNull() ?: return null
        return when (match.groupValues[2].lowercase()) {
            "px", "" -> number
            "em", "rem" -> number * EM
            else -> null
        }
    }

    companion object {
        private const val EM = 16.0
        private val INLINE_BOXES = setOf("inline-block", "inline-flex", "inline-grid")
        private val WHITESPACE = Regex("\\s+")
        private val LENGTH = Regex("""([+-]?(?:\d+\.?\d*|\.\d+))([a-zA-Z%]*)""")

        /** The style written in [declarations], an inline `style` attribute's value, later declarations winning. */
        fun of(declarations: String?) = ChordSheetHtmlStyle(
            declarations.orEmpty().split(';').mapNotNull { declaration ->
                val colon = declaration.indexOf(':')
                if (colon < 0) null else declaration.substring(0, colon).trim().lowercase() to declaration.substring(colon + 1).trim()
            }.toMap(),
        )
    }
}
