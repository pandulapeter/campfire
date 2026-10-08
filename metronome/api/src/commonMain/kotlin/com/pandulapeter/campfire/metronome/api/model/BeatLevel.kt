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
 * How one beat of the bar sounds. A muted beat still counts and still flashes, which is what lets a drummer practise
 * with only the two and the four sounding.
 *
 * @param id What a stored setting calls it; never changes once released.
 */
public enum class BeatLevel(public val id: String) {
    ACCENT("accent"),
    NORMAL("normal"),
    MUTED("muted");

    /** The level a tap on the beat moves it on to, round in a circle. */
    public fun next(): BeatLevel = entries[(ordinal + 1) % entries.size]

    public companion object {
        public fun fromId(id: String): BeatLevel? = entries.firstOrNull { it.id == id }
    }
}
