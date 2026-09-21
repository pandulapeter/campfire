/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.api.models

import com.pandulapeter.campfire.data.model.domain.Song

/**
 * One section of the sorted song list: the songs filed under one header, in the order the list shows them.
 *
 * The sections are cut by whoever sorts the list, see [ScreenData.songSections], because the two only work
 * together: a header comes up once in a list only for as long as the order keeps everything filed under it in one
 * run.
 */
data class SongSection(
    val header: Header,
    val songs: List<Song>,
) {

    sealed interface Header {

        /**
         * What tells this header from every other one of the same list, and stays what it is while songs come and
         * go under it - which is what a lazy list asks of an item's key.
         */
        val key: String

        /**
         * @param name The artist as the first song of the section spells it, which may be blank.
         * @param initial The first letter of the name in upper case, null where the name starts with anything else.
         */
        data class Artist(val name: String, val initial: Char?, override val key: String) : Header

        data class Letter(val letter: Char) : Header {
            override val key get() = letter.toString()
        }

        /** Every title that starts with something other than a letter: a digit, punctuation, an emoji. */
        data object Symbols : Header {
            override val key = ""
        }
    }
}
