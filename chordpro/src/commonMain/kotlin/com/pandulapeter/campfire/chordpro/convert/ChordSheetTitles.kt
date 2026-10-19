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
 * The shapes the first line of a copied chord page names its song in: `Riverlight Chords by The Invented Band`,
 * `Riverlight chords`, `The Invented Band - Riverlight`. A `by` is a credit only after the page's kind, since without
 * one it is as often part of the title ("Stand by Me"); a parenthesized tail ("Hallelujah (Live)") is part of the title
 * as often as it names someone, so it is kept. `Artist - Title` is read artist first, although both orders are common.
 */
internal object ChordSheetTitles {
    /** The title [line] names, and the artist where it names one too. */
    fun split(line: String): Pair<String, String?> {
        val text = line.trim()
        val kind = pageKind.matchEntire(text)
        val rest = kind?.groupValues?.get(1)?.trim() ?: text
        kind?.groupValues?.get(2)?.trim()?.takeIf { it.isNotEmpty() }?.let { return rest to it }
        val parts = rest.split(dash, limit = 2)
        return if (parts.size == 2 && parts.all { it.isNotBlank() }) parts[1].trim() to parts[0].trim() else rest to null
    }

    private const val VERSION = "(?:\\(ver(?:sion)? *[0-9]+\\)|v[0-9]+)"
    private val pageKind = Regex(
        "^(.+?)(?: +$VERSION)? +(?:chords and lyrics|lyrics and chords|chords?|tabs?|lyrics|akkordok|akkord|dalsz\u00f6vege|" +
            "dalsz\u00f6veg|acordes|cifra|accordi|akkorde|akordy)(?: +$VERSION)?(?: +(?:by|[-\u2013\u2014]) +(.+))?$",
        RegexOption.IGNORE_CASE,
    )
    private val dash = Regex(" +[-\u2013\u2014] +")
}
