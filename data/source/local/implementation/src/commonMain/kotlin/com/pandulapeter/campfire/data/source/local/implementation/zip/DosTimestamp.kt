/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.zip

/** A moment as the two MS-DOS fields a zip archive stores. */
internal data class DosTimestamp(val date: Int, val time: Int) {

    companion object {
        val EARLIEST = DosTimestamp(date = (1 shl 5) or 1, time = 0)
        private val LATEST = DosTimestamp(date = ((LAST_YEAR - FIRST_YEAR) shl 9) or (12 shl 5) or 31, time = (23 shl 11) or (59 shl 5) or 29)

        fun of(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int) = when {
            year < FIRST_YEAR -> EARLIEST
            year > LAST_YEAR -> LATEST
            else -> DosTimestamp(date = ((year - FIRST_YEAR) shl 9) or (month shl 5) or day, time = (hour shl 11) or (minute shl 5) or (second / 2).coerceAtMost(29))
        }

        private const val FIRST_YEAR = 1980
        private const val LAST_YEAR = 2107
    }
}
