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
 * Everything the click needs, complete: the engine reads no settings of its own.
 *
 * @param bpm Clicks of the bar per minute, within [BPM_RANGE].
 * @param beatLevels One per beat of [timeSignature]; a list of another length is padded or cut to fit it, the padding
 *   taken from the signature's defaults.
 * @param volume From 0 to 1, on top of the system's own volume; at 0 the clock and the beats go on with nothing
 *   sounding, for the visual beat alone - which is why such a click does not outlive the app leaving the front, see
 *   [canSound].
 */
data class MetronomePattern(
    val bpm: Int,
    val timeSignature: TimeSignature = TimeSignature.COMMON_TIME,
    val beatLevels: List<BeatLevel> = timeSignature.defaultBeatLevels(),
    val subdivision: Subdivision = Subdivision.NONE,
    val sound: MetronomeSound = MetronomeSound.CLICK,
    val volume: Float = 1f,
) {

    /** The level of the beat at [index] of the bar, whatever the length of [beatLevels]. */
    fun beatLevel(index: Int) = beatLevels.getOrNull(index) ?: timeSignature.defaultBeatLevels()[index]

    /**
     * Whether anything of this pattern is ever heard: a volume above zero and at least one beat of the bar not muted. A
     * subdivision carries its beat's level, so it never makes a muted bar audible.
     */
    val canSound: Boolean
        get() = volume > 0f && (0 until timeSignature.beats).any { beatLevel(it) != BeatLevel.MUTED }

    companion object {
        val BPM_RANGE = 30..300
        const val DEFAULT_BPM = 120

        fun coerceBpm(bpm: Int) = bpm.coerceIn(BPM_RANGE)
    }
}
