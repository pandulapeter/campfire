/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songEditor.search

/**
 * Where [query] occurs in [text], as the offsets the occurrences start at, each one [query]'s length long: ignoring the
 * case compares the letters one by one, so a match is never longer or shorter than what was typed. The occurrences do
 * not overlap, since a Replace all that wrote over two overlapping ones would write the second over half of the first's
 * replacement. A blank [query] matches nothing rather than every gap between two letters.
 */
internal fun searchMatchStarts(text: String, query: String, isMatchCase: Boolean): List<Int> {
    if (query.isEmpty()) return emptyList()
    val starts = mutableListOf<Int>()
    var index = text.indexOf(query, startIndex = 0, ignoreCase = !isMatchCase)
    while (index >= 0) {
        starts += index
        index = text.indexOf(query, startIndex = index + query.length, ignoreCase = !isMatchCase)
    }
    return starts
}

/**
 * Which of [matchStarts] the search is at with the field's selection starting at [selectionStart]: the first match that
 * starts there or after it, so that a search picks up from where the caret was rather than from the top, and the first
 * one of all past the last, as a search wraps around. -1 where there is no match at all.
 */
internal fun currentMatchIndex(matchStarts: List<Int>, selectionStart: Int): Int {
    if (matchStarts.isEmpty()) return -1
    val index = matchStarts.indexOfFirst { it >= selectionStart }
    return if (index == -1) 0 else index
}
