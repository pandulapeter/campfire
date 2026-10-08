/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.print

/** Preserve every character (including spaces used as chord anchors), splitting at words where there is room. */
internal fun wrapPrintText(text: String, width: Float, measure: (String) -> Float): List<String> {
    if (text.isEmpty()) return listOf("")
    if (measure(text) <= width) return listOf(text)
    val result = mutableListOf<String>()
    var start = 0
    while (start < text.length) {
        // Bound the search by a few screenfuls, rather than shaping the whole remaining line for every wrap.
        var upper = minOf(start + 32, text.length)
        while (upper < text.length && measure(text.substring(start, upper)) <= width) {
            upper = minOf(text.length, start + (upper - start) * 2)
        }
        var low = start + 1
        var high = upper
        var end = low
        while (low <= high) {
            val middle = (low + high) / 2
            if (measure(text.substring(start, middle)) <= width) {
                end = middle
                low = middle + 1
            } else {
                high = middle - 1
            }
        }
        if (end < text.length) {
            // Break after the last space or break opportunity, unless the word after it would not fit a line of its own
            // either, in which case the hard cut is the right one. The cut only ever moves back, so the fragments still
            // add up to the text and chord positions still map onto them by index.
            val space = maxOf(text.lastIndexOf(' ', end - 1), text.lastIndexOf('\u200B', end - 1))
            if (space > start) {
                val next = listOf(text.indexOf(' ', space + 1), text.indexOf('\u200B', space + 1))
                    .filter { it >= 0 }
                    .minOrNull() ?: text.length
                if (measure(text.substring(space + 1, next)) <= width) end = space + 1
            }
            // A cut inside a grapheme cluster would print a mark, a modifier or half a flag on its own, so it moves back to
            // the start of the cluster, or past its end where the cluster is all the fragment has.
            while (end - start > 1 && text.splitsClusterAt(end)) end--
            while (text.splitsClusterAt(end)) end++
        }
        result += text.substring(start, end)
        start = end
    }
    return result
}

/** Whether a cut between `index - 1` and `index` would separate a character from what is drawn as one with it. */
private fun String.splitsClusterAt(index: Int): Boolean {
    if (index <= 0 || index >= length) return false
    val char = this[index]
    if (char.isLowSurrogate() || char == '\u200D' || char == '\uFE0E' || char == '\uFE0F' || this[index - 1] == '\u200D') return true
    if (char.category in COMBINING_CATEGORIES) return true
    val codePoint = codePointAt(index)
    if (codePoint in SKIN_TONE_MODIFIERS) return true
    // Regional indicators pair up into flags from the start of their run, so a cut after an odd number of them is inside one.
    if (codePoint !in REGIONAL_INDICATORS) return false
    var count = 0
    var position = index
    while (position >= 2 && codePointAt(position - 2) in REGIONAL_INDICATORS) {
        count++
        position -= 2
    }
    return count % 2 == 1
}

private fun String.codePointAt(index: Int): Int =
    if (this[index].isHighSurrogate() && index + 1 < length && this[index + 1].isLowSurrogate()) {
        0x10000 + ((this[index].code - 0xD800) shl 10) + (this[index + 1].code - 0xDC00)
    } else {
        this[index].code
    }

private val COMBINING_CATEGORIES = setOf(
    CharCategory.NON_SPACING_MARK,
    CharCategory.ENCLOSING_MARK,
    CharCategory.COMBINING_SPACING_MARK,
)
private val SKIN_TONE_MODIFIERS = 0x1F3FB..0x1F3FF
private val REGIONAL_INDICATORS = 0x1F1E6..0x1F1FF
