/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songEditor

/**
 * Where [offset] in [before] is in [after]. An edit that kept the number of lines (chords respelled by a save on
 * any number of lines, a header value changed) is mapped line by line, so that the caret stays on its line next to
 * its text however many other lines changed; one that added or removed lines (a tag line put in) is mapped as one
 * changed stretch: an offset in the unchanged start stays, one in the unchanged end moves by the change in length,
 * and one inside the changed stretch goes to the end of what replaced it. A document rewritten throughout maps its
 * own way (transposition, Prettify).
 */
internal fun editedOffset(before: String, after: String, offset: Int): Int {
    val clamped = offset.coerceIn(0, before.length)
    if (before.count { it == '\n' } != after.count { it == '\n' }) return stretchOffset(before, after, clamped)
    val lineStartBefore = before.lastIndexOf('\n', clamped - 1) + 1
    val line = before.substring(0, lineStartBefore).count { it == '\n' }
    // The start of the same line in the edited text.
    var lineStartAfter = 0
    repeat(line) { lineStartAfter = after.indexOf('\n', lineStartAfter) + 1 }
    val lineBefore = before.substring(lineStartBefore, before.indexOf('\n', lineStartBefore).let { if (it < 0) before.length else it })
    val lineAfter = after.substring(lineStartAfter, after.indexOf('\n', lineStartAfter).let { if (it < 0) after.length else it })
    return lineStartAfter + stretchOffset(lineBefore, lineAfter, clamped - lineStartBefore)
}

/**
 * [offset] across one changed stretch of [before], see [editedOffset]. The common prefix and suffix step back rather
 * than end inside a surrogate pair, so no boundary splits one.
 */
private fun stretchOffset(before: String, after: String, offset: Int): Int {
    val prefix = before.commonPrefixWith(after).length
    val suffix = before.commonSuffixWith(after).length.coerceAtMost(minOf(before.length, after.length) - prefix)
    val changedEndBefore = before.length - suffix
    val changedEndAfter = after.length - suffix
    return when {
        offset <= prefix -> offset
        offset >= changedEndBefore -> offset + (after.length - before.length)
        else -> changedEndAfter
    }.coerceIn(0, after.length)
}
