/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro

/**
 * The part of an old text that one edit replaced, and where its replacement ends in the new text; the ends are exclusive
 * and the start is the same on both sides, since everything before it is the prefix the two texts share. The caches
 * the editor calls on every keystroke use it to tell which line a keystroke touched.
 */
internal data class ChordProTextChange(val oldStart: Int, val oldEnd: Int, val newEnd: Int) {

    companion object {

        /** The span between the longest common prefix and the longest common suffix of [old] and [new]. */
        fun between(old: String, new: String): ChordProTextChange {
            var start = 0
            while (start < old.length && start < new.length && old[start] == new[start]) start++
            var oldEnd = old.length
            var newEnd = new.length
            while (oldEnd > start && newEnd > start && old[oldEnd - 1] == new[newEnd - 1]) {
                oldEnd--
                newEnd--
            }
            return ChordProTextChange(start, oldEnd, newEnd)
        }
    }
}
