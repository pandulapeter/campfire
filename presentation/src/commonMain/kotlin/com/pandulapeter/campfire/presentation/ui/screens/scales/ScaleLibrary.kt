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
 * What the Scales tab lists for [query]: the scales of the family chip at [familyIndex] while it is blank, and otherwise
 * every scale whose name ([nameOf], in the app's language), English name or other name holds it, ignoring case and
 * accents, whatever family it is in, so that `hijaz` finds the Phrygian dominant and `dur` the major scale in
 * Hungarian. The ones found by a name come before the ones found only by another name, each in catalogue order.
 */
internal fun scaleLibraryResults(familyIndex: Int, query: String, nameOf: (Scale) -> String): List<Scale> {
    val typed = folded(query.trim())
    if (typed.isEmpty()) return scaleLibraryScales(familyIndex)
    val byName = Scales.all.filter { scale -> folded(nameOf(scale)).contains(typed) || folded(scale.id.replace('_', ' ')).contains(typed) }
    val byAlias = Scales.all.filter { scale -> scale !in byName && scale.aliases.any { folded(it).contains(typed) } }
    return byName + byAlias
}

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
