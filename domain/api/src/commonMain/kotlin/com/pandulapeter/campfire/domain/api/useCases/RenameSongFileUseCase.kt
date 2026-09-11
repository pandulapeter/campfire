/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.api.useCases

import com.pandulapeter.campfire.data.model.domain.Song

interface RenameSongFileUseCase {

    /**
     * Moves the song's file to the name its own metadata gives it, and takes everything that named the old one with
     * it: the entries of every setlist holding the song, and its saved transposition.
     *
     * Asked for rather than done on the user's behalf ([Song.canUpdateFileName] is what offers it), because a file
     * name is the identity of a song: it is what a setlist points at, what sync addresses and, on the platforms
     * where the library is a folder the user can open, something they may have chosen themselves.
     *
     * @return The new file name, or null if nothing moved - the file could not be read, or it was already named
     *   that way. The caller is what still holds the old name: the screens showing the song, and the caches keyed
     *   by it.
     */
    suspend operator fun invoke(song: Song): String?
}
