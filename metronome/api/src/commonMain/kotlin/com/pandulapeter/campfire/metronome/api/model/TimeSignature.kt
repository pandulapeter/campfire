/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.metronome.api.model

/**
 * A bar of [beats] clicks, each a [unit] note. The unit decides nothing about the timing - the tempo counts the
 * clicks of the bar, so 6/8 at 120 is six clicks a bar at 120 a minute - but it decides the default accents, since a
 * compound meter is felt in groups of three.
 */
data class TimeSignature(
    val beats: Int,
    val unit: Int,
) {
    init {
        require(beats in BEATS_RANGE) { "beats out of range: $beats" }
        require(unit in UNITS) { "unit out of range: $unit" }
    }

    /** "6/8", which is also how a signature is stored. */
    override fun toString() = "$beats/$unit"

    /**
     * The accents a bar of this signature gets until the user draws their own: an accent on the first beat, and in a
     * compound meter (6/8, 9/8, 12/8) a lighter accent at the start of every group of three, read as accents too.
     */
    fun defaultBeatLevels() = List(beats) { index ->
        when {
            index == 0 -> BeatLevel.ACCENT
            isCompound && index % 3 == 0 -> BeatLevel.ACCENT
            else -> BeatLevel.NORMAL
        }
    }

    private val isCompound get() = unit >= 8 && beats >= 6 && beats % 3 == 0

    companion object {
        val BEATS_RANGE = 1..16
        val UNITS = listOf(1, 2, 4, 8, 16)
        val COMMON_TIME = TimeSignature(4, 4)

        /** Reads what [toString] writes; null for anything else, a signature out of range included. */
        fun parse(text: String): TimeSignature? {
            val parts = text.trim().split('/')
            if (parts.size != 2) return null
            val beats = parts[0].trim().toIntOrNull() ?: return null
            val unit = parts[1].trim().toIntOrNull() ?: return null
            return if (beats in BEATS_RANGE && unit in UNITS) TimeSignature(beats, unit) else null
        }
    }
}
