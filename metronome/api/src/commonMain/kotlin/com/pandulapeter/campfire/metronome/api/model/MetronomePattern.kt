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
 * @param volume From 0 to 1, on top of the system's own volume.
 * @param isMuted Keeps the clock and the beats going with nothing sounding, for the visual beat alone.
 */
data class MetronomePattern(
    val bpm: Int,
    val timeSignature: TimeSignature = TimeSignature.COMMON_TIME,
    val beatLevels: List<BeatLevel> = timeSignature.defaultBeatLevels(),
    val subdivision: Subdivision = Subdivision.NONE,
    val sound: MetronomeSound = MetronomeSound.CLICK,
    val volume: Float = 1f,
    val isMuted: Boolean = false,
) {

    /** The level of the beat at [index] of the bar, whatever the length of [beatLevels]. */
    fun beatLevel(index: Int) = beatLevels.getOrNull(index) ?: timeSignature.defaultBeatLevels()[index]

    companion object {
        val BPM_RANGE = 30..300
        const val DEFAULT_BPM = 120

        fun coerceBpm(bpm: Int) = bpm.coerceIn(BPM_RANGE)
    }
}
