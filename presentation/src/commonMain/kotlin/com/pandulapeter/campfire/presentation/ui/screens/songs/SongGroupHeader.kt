/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songs

import com.pandulapeter.campfire.domain.api.models.SongSection

/**
 * What a group of the song list is headed by: a section the sort cut the library into, or the songs ticked in the
 * selection mode, which lead the list under a header of their own (see [withSelectionLeading]). The domain's sections
 * stay what the sort cut, so the selection is a header of the list's rather than one more kind of theirs.
 */
sealed interface SongGroupHeader {

    /** What tells this header from every other one of the list, as a lazy list asks of an item's key. */
    val key: String

    /** The header's key in the grid, which no song's key can be. */
    val itemKey get() = "header_$key"

    data class Section(val header: SongSection.Header) : SongGroupHeader {
        override val key get() = header.key
    }

    data object Selection : SongGroupHeader {
        /** A character no section's key holds, since an artist's key is made of the artist's name. */
        override val key = "\u0000selection"
    }
}

/** What a header of the song list is to the grid, and so to its pushed copy. */
internal const val SONG_HEADER_CONTENT_TYPE = "header"
