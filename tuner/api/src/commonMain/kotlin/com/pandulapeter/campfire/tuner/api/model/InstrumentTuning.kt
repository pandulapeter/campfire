/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.tuner.api.model

/**
 * The tunings the tuner offers, each string a MIDI note number, from the highest numbered string to the first, the way
 * a tuning is written (`gCEA`: a ukulele's and a banjo's high string is first, since that is where it is strung). [id]
 * is what a stored setting names one by; chromatic tuning is no tuning at all, `null` where one is asked for.
 */
public enum class InstrumentTuning(
    public val id: String,
    public val strings: List<Int>,
) {
    GUITAR("guitar", listOf(40, 45, 50, 55, 59, 64)),
    GUITAR_DROP_D("guitar_drop_d", listOf(38, 45, 50, 55, 59, 64)),
    BASS("bass", listOf(28, 33, 38, 43)),
    BASS_FIVE_STRING("bass_five_string", listOf(23, 28, 33, 38, 43)),
    UKULELE("ukulele", listOf(67, 60, 64, 69)),
    UKULELE_LOW_G("ukulele_low_g", listOf(55, 60, 64, 69)),
    VIOLIN("violin", listOf(55, 62, 69, 76)),
    MANDOLIN("mandolin", listOf(55, 62, 69, 76)),
    BANJO("banjo", listOf(67, 50, 55, 59, 62)),
    ;

    public companion object {
        /** What a stored setting names chromatic tuning by. */
        public const val CHROMATIC_ID: String = "chromatic"

        /** The tuning [id] names, null for chromatic and for an id this version does not know. */
        public fun fromId(id: String): InstrumentTuning? = entries.firstOrNull { it.id == id }
    }
}
