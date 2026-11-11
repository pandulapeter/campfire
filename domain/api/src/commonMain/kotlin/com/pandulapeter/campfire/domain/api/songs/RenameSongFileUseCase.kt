/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.api.songs

import com.pandulapeter.campfire.data.model.song.Song
import com.pandulapeter.campfire.domain.api.songs.SongFileRename

interface RenameSongFileUseCase {

    /**
     * Moves the song's file to the name its own metadata gives it, and takes everything that named the old one with
     * it: the entries of every setlist holding the song, and its saved transposition.
     *
     * Asked for rather than done on the user's behalf ([Song.canUpdateFileName] is what offers it), because a file
     * name is the identity of a song: it is what a setlist points at, what sync addresses and, on the platforms
     * where the library is a folder the user can open, something they may have chosen themselves.
     *
     * The new file is written before any reference follows it, and the old one deleted only after all of them have, so
     * that nothing ever names a file that is not there, a process killed half way included: that leaves the song
     * twice at worst. The rename is not undone when a reference cannot be followed: every setlist and the
     * transposition are still attempted, and the result says whether all of them were. Those that were not go on
     * naming the old file, which is then kept. Once the new file is written, the one failure thrown is a deletion of
     * the old file that failed, after every reference has followed the new one: the song is then there twice, and the
     * caller, still on the old name, on a file that is there.
     *
     * @return The new file name and whether everything followed it, or null if nothing was written - the file could
     *   not be read, or it was already named that way. The caller is what still holds the old name: the screens
     *   showing the song, and the caches keyed by it.
     */
    suspend operator fun invoke(song: Song): SongFileRename?
}
