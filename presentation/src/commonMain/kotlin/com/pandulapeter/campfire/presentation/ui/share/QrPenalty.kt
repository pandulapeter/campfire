/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.share

import kotlin.math.abs

/**
 * The standard's penalty score of a masked symbol (section 7.8.3), lower being easier to read: long runs of one color,
 * 2 × 2 blocks of one color, shapes a reader could take for a finder pattern, and a dark share far from half. Beyond
 * the symbol's edge counts as light, as the quiet zone is.
 */
internal fun qrPenalty(modules: BooleanArray, size: Int): Int {
    var penalty = 0
    for (line in 0 until size) {
        penalty += linePenalty(size) { modules[line * size + it] }
        penalty += linePenalty(size) { modules[it * size + line] }
    }
    for (y in 0 until size - 1) {
        for (x in 0 until size - 1) {
            val color = modules[y * size + x]
            if (modules[y * size + x + 1] == color && modules[(y + 1) * size + x] == color && modules[(y + 1) * size + x + 1] == color) {
                penalty += 3
            }
        }
    }
    val dark = modules.count { it }
    return penalty + abs(dark * 2 - modules.size) * 10 / modules.size * 10
}

/** The run and finder-like pattern penalties of one row or column, read through [isDark]. */
private fun linePenalty(size: Int, isDark: (Int) -> Boolean): Int {
    var penalty = 0
    var run = 0
    var previous = false
    for (index in 0 until size) {
        val current = isDark(index)
        if (index > 0 && current == previous) {
            run++
        } else {
            if (run >= 5) penalty += run - 2
            run = 1
            previous = current
        }
    }
    if (run >= 5) penalty += run - 2
    fun isLight(from: Int, until: Int) = (from until until).none { it in 0 until size && isDark(it) }
    for (start in 0..size - 7) {
        val isFinderLike = isDark(start) && !isDark(start + 1) && isDark(start + 2) && isDark(start + 3) && isDark(start + 4) &&
            !isDark(start + 5) && isDark(start + 6)
        if (isFinderLike && (isLight(start - 4, start) || isLight(start + 7, start + 11))) penalty += 40
    }
    return penalty
}
