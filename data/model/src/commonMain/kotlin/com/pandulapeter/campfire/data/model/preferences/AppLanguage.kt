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

/** The language the app's own texts are in ([UserPreferences.language]): one with strings of its own, or the system's. */
enum class AppLanguage(val id: String) {
    ENGLISH("en"),
    HUNGARIAN("hu"),
    ROMANIAN("ro"),
    GERMAN("de"),
    SYSTEM_DEFAULT("system_default"),
}
