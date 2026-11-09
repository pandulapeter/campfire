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

/**
 * Reads a colour the way ChordPro writes one in `{chordcolour}` and `{textcolour}`: `#RRGGBB` (or the short `#RGB`), or a name. The names
 * are the colours of CSS a song is likely to ask for; ChordPro's own reference knows them by these names too.
 */
internal object ChordProColors {

    /**
     * The colour [value] names as `0xRRGGBB`, or null where it names none: an empty value, ChordPro's `foreground`
     * (whatever the reader's text is), and a value this module cannot read, which the reader's own colour stands in for
     * rather than a guess.
     */
    fun parse(value: String?): Int? {
        val written = value?.trim()?.lowercase().orEmpty()
        if (!written.startsWith('#')) return namedColors[written.filterNot { it.isWhitespace() || it == '-' || it == '_' }]
        val digits = written.drop(1)
        if (digits.any { it !in HEX_DIGITS }) return null
        return when (digits.length) {
            6 -> digits.toInt(16)
            3 -> digits.fold(0) { color, digit -> (color shl 8) or (digit.digitToInt(16) * 0x11) }
            else -> null
        }
    }

    private const val HEX_DIGITS = "0123456789abcdef"

    private val namedColors = mapOf(
        "black" to 0x000000,
        "white" to 0xFFFFFF,
        "gray" to 0x808080,
        "grey" to 0x808080,
        "darkgray" to 0xA9A9A9,
        "darkgrey" to 0xA9A9A9,
        "lightgray" to 0xD3D3D3,
        "lightgrey" to 0xD3D3D3,
        "silver" to 0xC0C0C0,
        "red" to 0xFF0000,
        "darkred" to 0x8B0000,
        "maroon" to 0x800000,
        "crimson" to 0xDC143C,
        "orange" to 0xFFA500,
        "darkorange" to 0xFF8C00,
        "orangered" to 0xFF4500,
        "gold" to 0xFFD700,
        "yellow" to 0xFFFF00,
        "olive" to 0x808000,
        "lime" to 0x00FF00,
        "green" to 0x008000,
        "darkgreen" to 0x006400,
        "forestgreen" to 0x228B22,
        "teal" to 0x008080,
        "cyan" to 0x00FFFF,
        "aqua" to 0x00FFFF,
        "turquoise" to 0x40E0D0,
        "blue" to 0x0000FF,
        "darkblue" to 0x00008B,
        "navy" to 0x000080,
        "royalblue" to 0x4169E1,
        "steelblue" to 0x4682B4,
        "skyblue" to 0x87CEEB,
        "indigo" to 0x4B0082,
        "purple" to 0x800080,
        "violet" to 0xEE82EE,
        "magenta" to 0xFF00FF,
        "fuchsia" to 0xFF00FF,
        "pink" to 0xFFC0CB,
        "hotpink" to 0xFF69B4,
        "deeppink" to 0xFF1493,
        "brown" to 0xA52A2A,
        "chocolate" to 0xD2691E,
        "tan" to 0xD2B48C,
    )
}
