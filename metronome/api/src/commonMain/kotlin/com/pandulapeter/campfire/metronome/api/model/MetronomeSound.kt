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
 * What a click sounds like. Every sound is synthesized rather than shipped as a recording, so it is the same on every
 * platform and costs the web build nothing to download.
 *
 * @param id What a stored setting calls it; never changes once released.
 */
public enum class MetronomeSound(public val id: String) {
    CLICK("click"),
    WOODBLOCK("woodblock"),
    BEEP("beep"),
    HI_HAT("hi_hat"),
    COWBELL("cowbell");

    public companion object {
        public fun fromId(id: String): MetronomeSound? = entries.firstOrNull { it.id == id }
    }
}
