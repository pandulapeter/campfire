/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.setlists

/** The grid key of one song inside one setlist, since a song can appear in several of them. */
internal class SetlistItemKey(val string: String?) {

    constructor(setlistFileName: String, songFileName: String) : this("$setlistFileName$TOKEN$songFileName")

    private val parts = string?.split(TOKEN)?.takeIf { it.size == 2 }

    val setlistFileName: String? = parts?.first()

    val songFileName: String? = parts?.last()

    companion object {
        // Contains characters no normalized name can hold, so it can never occur inside a setlist's file name.
        private const val TOKEN = "#*#"

        /** What a setlist's header is to the grid, and to the cards' fade, which is measured from the setlist being read. */
        const val HEADER_CONTENT_TYPE = "setlist_header"

        private const val HEADER_PREFIX = "setlist_"
        private const val DESCRIPTION_PREFIX = "description_"
        private const val CHOOSE_SONGS_PREFIX = "choose_songs_"

        /** The grid key of the header of the setlist saved as [setlistFileName]. */
        fun header(setlistFileName: String) = HEADER_PREFIX + setlistFileName

        fun description(setlistFileName: String) = DESCRIPTION_PREFIX + setlistFileName

        fun chooseSongs(setlistFileName: String) = CHOOSE_SONGS_PREFIX + setlistFileName

        /**
         * The setlist the grid item of [key] belongs to: its header, its description, one of its songs or its Choose
         * songs row; null for an item of no setlist's. A song's key is read first, since a setlist's own file name may
         * start like one of the others.
         */
        fun setlistFileNameOf(key: Any?): String? {
            val string = key as? String ?: return null
            SetlistItemKey(string).setlistFileName?.let { return it }
            return listOf(HEADER_PREFIX, DESCRIPTION_PREFIX, CHOOSE_SONGS_PREFIX).firstOrNull { string.startsWith(it) }?.let { string.removePrefix(it) }
        }
    }
}
