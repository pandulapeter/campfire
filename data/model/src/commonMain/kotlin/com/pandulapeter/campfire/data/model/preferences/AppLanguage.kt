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
 * The language the app's own texts are in ([UserPreferences.language]): one with strings of its own, or the system's.
 *
 * [layoutDirection] is the one place the interface's direction is decided: a language is laid out from the right
 * because its entry says so, never because code names it. [SYSTEM_DEFAULT]'s is never read, since the system's
 * language is resolved to one of the other entries first.
 */
enum class AppLanguage(val id: String, val layoutDirection: Direction) {
    ENGLISH("en", Direction.LEFT_TO_RIGHT),
    HUNGARIAN("hu", Direction.LEFT_TO_RIGHT),
    ROMANIAN("ro", Direction.LEFT_TO_RIGHT),
    GERMAN("de", Direction.LEFT_TO_RIGHT),
    SPANISH("es", Direction.LEFT_TO_RIGHT),
    FRENCH("fr", Direction.LEFT_TO_RIGHT),
    ITALIAN("it", Direction.LEFT_TO_RIGHT),
    SYSTEM_DEFAULT("system_default", Direction.LEFT_TO_RIGHT),
    ;

    /** Which edge a language's interface starts from. */
    enum class Direction {
        LEFT_TO_RIGHT,
        RIGHT_TO_LEFT,
    }
}
