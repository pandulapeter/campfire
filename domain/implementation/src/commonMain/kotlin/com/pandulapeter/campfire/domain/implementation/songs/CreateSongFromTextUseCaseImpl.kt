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

import com.pandulapeter.campfire.chordpro.ChordProParser
import com.pandulapeter.campfire.chordpro.model.displayTitle
import com.pandulapeter.campfire.data.model.song.Song
import com.pandulapeter.campfire.data.repository.api.SongRepository
import com.pandulapeter.campfire.domain.api.songs.CreateSongFromTextUseCase
import org.koin.core.annotation.Factory

@Factory
internal class CreateSongFromTextUseCaseImpl(
    private val songRepository: SongRepository,
) : CreateSongFromTextUseCase {

    override suspend operator fun invoke(text: String): Song {
        val metadata = ChordProParser.parseMetadata(text)
        // Written as it is, since it is what the editor holds and the editor compares the file with what it shows.
        return songRepository.createSong(
            title = metadata.displayTitle(fallback = ""),
            artist = metadata.artist.orEmpty().trim(),
            text = text,
        )
    }
}
