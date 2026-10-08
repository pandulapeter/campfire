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
 * The quieter clicks between the beats.
 *
 * @param id What a stored setting calls it; never changes once released.
 * @param clicksPerBeat How many clicks a beat is cut into, the beat's own included.
 */
public enum class Subdivision(
    public val id: String,
    public val clicksPerBeat: Int,
) {
    NONE("none", 1),
    EIGHTHS("eighths", 2),
    TRIPLETS("triplets", 3),
    SIXTEENTHS("sixteenths", 4);

    public companion object {
        public fun fromId(id: String): Subdivision? = entries.firstOrNull { it.id == id }
    }
}
