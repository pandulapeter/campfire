/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.implementation.songs

import com.pandulapeter.campfire.data.model.Logger
import com.pandulapeter.campfire.data.model.share.SharedLinkKind
import com.pandulapeter.campfire.data.model.song.Song
import com.pandulapeter.campfire.data.repository.api.SetlistRepository
import com.pandulapeter.campfire.data.repository.api.SharedLinksRepository
import com.pandulapeter.campfire.data.repository.api.SongRepository
import com.pandulapeter.campfire.data.repository.api.UserPreferencesRepository
import com.pandulapeter.campfire.domain.api.songs.SongFileRename
import com.pandulapeter.campfire.domain.api.songs.RenameSongFileUseCase
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Factory

@Factory
internal class RenameSongFileUseCaseImpl(
    private val songRepository: SongRepository,
    private val setlistRepository: SetlistRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val sharedLinksRepository: SharedLinksRepository,
    private val logger: Logger,
) : RenameSongFileUseCase {

    /**
     * The mirror image of `DeleteSongUseCaseImpl`: the same places refer to a song by its file name, and where a
     * deletion drops those references a rename follows them. The order is what keeps a process killed half way from
     * breaking any of them: the new file is written first, then every reference follows it, and only then is the old
     * file deleted - a kill anywhere leaves the song twice at worst, both copies whole, and every reference naming a
     * file that is there. If the write fails there is nothing to undo.
     *
     * Once the new file is written every reference is attempted even when an earlier one could not be written, and
     * whether any failed is reported together at the end, in the result rather than as an exception: one setlist that
     * cannot be saved would otherwise leave every setlist after it, and the transposition, on the old name. A reference
     * that did not follow still names the old file, so the old file is then kept rather than deleted, the same as a
     * kill would leave it. For the same reason the walk is not cancellable once the new file is there: a screen going
     * away half way through would leave the rest of the references on the old name just the same. Which setlists hold
     * the song is asked of the files rather than the cache (see `followSongReferences`). A deletion of the old file that
     * fails is thrown, after every reference has followed the new one; both files are then listed.
     */
    override suspend operator fun invoke(song: Song): SongFileRename? {
        val renamed = songRepository.copySongToItsName(song) ?: return null
        return withContext(NonCancellable) {
            // A link to the song follows it under its new name, which only this device can tell from a deletion.
            if (renamed.fileName != song.fileName) sharedLinksRepository.followRename(SharedLinkKind.SONG, song.fileName, renamed.fileName)
            val haveReferencesFollowed = followSongReferences(
                setlistRepository,
                userPreferencesRepository,
                logger,
                fileName = song.fileName,
                newFileName = renamed.fileName,
            )
            songRepository.finishSongRename(fileName = song.fileName, renamed = renamed, shouldKeepOldFile = !haveReferencesFollowed)
            SongFileRename(fileName = renamed.fileName, haveReferencesFollowed = haveReferencesFollowed)
        }
    }
}
