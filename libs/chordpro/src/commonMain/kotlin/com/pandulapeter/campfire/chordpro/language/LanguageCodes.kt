/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.language

/**
 * The two letter language codes ISO 639-1 has replaced, and the ones that replaced them: Indonesian, Hebrew, Yiddish,
 * and Moldavian were `in`, `iw`, `ji`, and `mo`, and those are still what a JVM writes for them, Android's system
 * language included, where every other platform says `id`, `he`, `yi`, and `ro`.
 *
 * Every code the app compares with another goes through [current] first — the system's language before it picks the
 * app's, and a song's `{meta: language}` before the library files it — so that one language is never two.
 */
public object LanguageCodes {

    private val replacements = mapOf(
        "in" to "id",
        "iw" to "he",
        "ji" to "yi",
        "mo" to "ro",
    )

    /** [code] as the standard writes it today: its replacement if it is a retired code, else [code] itself. */
    public fun current(code: String): String = replacements[code] ?: code
}
