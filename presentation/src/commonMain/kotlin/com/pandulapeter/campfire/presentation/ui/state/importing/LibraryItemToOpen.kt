/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state.importing

/**
 * What an import that turned out to hold only what the library already has opens instead of a review, see `librarySong`
 * and `librarySetlist`.
 */
internal sealed interface LibraryItemToOpen {

    /** The library's own song, by its file name, opened on its details screen. */
    data class Song(val fileName: String) : LibraryItemToOpen

    /** The library's own setlist, by its file name, scrolled to on the setlists screen. */
    data class Setlist(val fileName: String) : LibraryItemToOpen
}
