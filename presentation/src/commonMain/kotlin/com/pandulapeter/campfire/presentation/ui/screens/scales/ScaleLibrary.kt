/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.scales

import androidx.compose.runtime.Immutable
import com.pandulapeter.campfire.chordpro.chords.ChordNotation
import com.pandulapeter.campfire.chordpro.chords.ChordProNotation
import com.pandulapeter.campfire.chordpro.scales.Scale
import com.pandulapeter.campfire.chordpro.scales.ScaleFamily
import com.pandulapeter.campfire.chordpro.scales.Scales
import com.pandulapeter.campfire.data.model.library.withoutAccent

/**
 * The scales the Scales tab lists for the family chip at [familyIndex]: the common ones for the first chip, then each
 * [ScaleFamily] in its order. An index past the last family is the first chip's, so a family a later version drops
 * cannot leave the page empty.
 */
internal fun scaleLibraryScales(familyIndex: Int): List<Scale> =
    ScaleFamily.entries.getOrNull(familyIndex - 1)?.let(Scales::of) ?: Scales.common

/**
 * What the Scales tab lists: [scales], each drawn on [rootName], a note in the standard notation, and [query], the part
 * of the search they were found by, which the heading over them quotes.
 */
@Immutable
internal data class ScaleLibraryResults(
    val rootName: String,
    val query: String,
    val scales: List<Scale>,
)

/**
 * What the Scales tab lists for [query] in the key [keyName]: the scales of the family chip at [familyIndex] while it is
 * blank, and otherwise every scale whose name ([nameOf], in the app's language), English name or other name (in the
 * app's language, [aliasesOf], or in English, the catalogue's) holds it, ignoring case and accents, whatever family it is
 * in, so that `hijaz` finds the Phrygian dominant and `dur` the major scale in Hungarian. The ones found by a name come
 * before the ones found only by another name, each in catalogue order.
 *
 * A search that starts with a note in [notation], set apart by a space or a hyphen, finds the scales the rest names on
 * that note, whatever key is picked, as the Chords tab finds a chord the key does not list: `d dorian`, or `a-moll` as
 * a Hungarian title writes it. Where the rest names nothing, the whole search is a name, so a scale whose name starts
 * with a word that reads as a note is still found by it.
 */
internal fun scaleLibraryResults(
    familyIndex: Int,
    query: String,
    keyName: String,
    notation: ChordNotation,
    nameOf: (Scale) -> String,
    aliasesOf: (Scale) -> String?,
): ScaleLibraryResults {
    val typed = query.trim()
    if (typed.isEmpty()) return ScaleLibraryResults(rootName = keyName, query = typed, scales = scaleLibraryScales(familyIndex))
    ROOTED_SEARCH.matchEntire(typed)?.let { match ->
        val root = ChordProNotation.standardName(match.groupValues[1].replaceFirstChar(Char::uppercaseChar), notation)
        if (root != null && NOTE.matches(root)) {
            val rest = match.groupValues[2]
            val scales = scalesNamed(rest, nameOf, aliasesOf)
            if (scales.isNotEmpty()) return ScaleLibraryResults(rootName = root, query = rest, scales = scales)
        }
    }
    return ScaleLibraryResults(rootName = keyName, query = typed, scales = scalesNamed(typed, nameOf, aliasesOf))
}

private fun scalesNamed(query: String, nameOf: (Scale) -> String, aliasesOf: (Scale) -> String?): List<Scale> {
    val typed = folded(query)
    val byName = Scales.all.filter { scale -> folded(nameOf(scale)).contains(typed) || folded(scale.id.replace('_', ' ')).contains(typed) }
    val byAlias = Scales.all.filter { scale ->
        scale !in byName && (scale.aliases.any { folded(it).contains(typed) } || aliasesOf(scale)?.let { folded(it).contains(typed) } == true)
    }
    return byName + byAlias
}

/** A search's first word, set apart by spaces or a hyphen, and the rest of it. */
private val ROOTED_SEARCH = Regex("""([^\s-]+)[\s-]+(\S.*)""")

/** A chord name in the standard notation that is a note alone, with no quality or bass. */
private val NOTE = Regex("[A-G][#b]?")

/**
 * [text] in lowercase with the accents of Latin letters taken off, as a search compares it, and the signs a name is
 * written with as a keyboard types them: `#` for ♯ and `b` for ♭, a ♮ dropped (the id spells it `natural`), and a hyphen
 * for the dash of "whole–half".
 */
private fun folded(text: String) = text.lowercase().map { char ->
    when (char) {
        '♯' -> "#"
        '♭' -> "b"
        '♮' -> ""
        '–' -> "-"
        else -> char.withoutAccent().toString()
    }
}.joinToString("")
