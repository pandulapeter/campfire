/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.model.domain

/**
 * One language the library sings in, as the filter controls need it. Like [Tag] it is stored nowhere: the languages
 * of a library are whatever its songs declare, counted per scan. The name shown for a [code] is not here either —
 * it is whatever the platform calls that language in the language the app is set to, see `:presentation`.
 */
data class SongLanguage(
    /** A lowercase ISO code, or [UNKNOWN] for the songs that declare no language at all. */
    val code: String,
    val songCount: Int,
) {

    companion object {

        /**
         * The songs that say nothing about their language, which the filter offers as a group of their own — it is
         * how the user finds what is still to be filled in. `und` is the ISO code for "undetermined" and is read as
         * "no language" by `:chordpro`, so no song can ever carry it and the two can never be confused.
         */
        const val UNKNOWN = "und"
    }
}
