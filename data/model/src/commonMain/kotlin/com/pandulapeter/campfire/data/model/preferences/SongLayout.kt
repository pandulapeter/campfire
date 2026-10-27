/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.model.preferences

/**
 * How the song details screen lays a song out ([UserPreferences.songLayout]): [PAGES] flows it into the columns and pages
 * a pedal steps through, [CONTINUOUS] stacks it in one column that scrolls by itself at the song's own pace.
 */
enum class SongLayout(val id: String) {
    PAGES("pages"),
    CONTINUOUS("continuous"),
    ;

    companion object {
        /**
         * The speeds `UserPreferences.autoScrollSpeeds` may hold, in percent of the song's own pace: its `{duration}`, or
         * the one estimated from its lines and its tempo. A share of that rather than a speed on screen, so that it means
         * the same at every text size, on every screen, and after a verse is added.
         */
        val AUTO_SCROLL_SPEED_RANGE = 25..400

        /** The song's own pace, which `UserPreferences.autoScrollSpeeds` holds no entry for. */
        const val DEFAULT_AUTO_SCROLL_SPEED = 100
    }
}
