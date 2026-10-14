/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state

import com.pandulapeter.campfire.data.model.song.Song

/**
 * The whole library by file name, together with whether it has been read, as one value: see `LibraryState.songLookup`.
 *
 * @param isLibraryRead False while the library is being read for the first time, as `LibraryState.isLoading` says.
 */
internal data class SongLookup(
    val songsByFileName: Map<String, Song>,
    val isLibraryRead: Boolean,
) {
    companion object {
        val Empty = SongLookup(emptyMap(), isLibraryRead = false)
    }
}
