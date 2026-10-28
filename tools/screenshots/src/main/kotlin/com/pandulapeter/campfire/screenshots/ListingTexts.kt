/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.screenshots

import java.io.File

/** What a listing image says in one language: its [headline], its lines broken as written, and its [chips]' texts. */
internal class ListingText(
    val headline: String,
    val chips: List<String>,
)

/**
 * The listing's texts in [file], one of `documentation/store-listings/screenshot-texts`, by shot id: a shot's headline
 * is the block under its `## <id> headline (…)` heading and its chips the blocks under `## <id> chips (…)`, the same
 * headings `check_store_listings.py` holds to their limits.
 */
internal fun readListingTexts(file: File): Map<String, ListingText> {
    val headlines = mutableMapOf<String, String>()
    val chips = mutableMapOf<String, MutableList<String>>()
    var field: Pair<String, String>? = null
    var block: MutableList<String>? = null
    file.readLines().forEach { line ->
        val current = block
        when {
            line.startsWith("```") && current == null -> block = mutableListOf()
            line.startsWith("```") && current != null -> {
                val (id, kind) = requireNotNull(field) { "A block in $file is under no shot's heading." }
                val text = current.joinToString("\n")
                if (kind == "headline") headlines[id] = text else chips.getOrPut(id) { mutableListOf() } += text
                block = null
            }
            current != null -> current += line
            line.startsWith("## ") -> field = HEADING.find(line)?.destructured?.let { (id, kind) -> id to kind }
        }
    }
    return headlines.mapValues { (id, headline) -> ListingText(headline = headline, chips = chips[id].orEmpty()) }
}

private val HEADING = Regex("""^## (\S+) (headline|chips) \(""")
