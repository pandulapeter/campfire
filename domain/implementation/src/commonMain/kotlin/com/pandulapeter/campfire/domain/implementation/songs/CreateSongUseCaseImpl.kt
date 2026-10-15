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
import com.pandulapeter.campfire.chordpro.edit.ChordProMetadataFields
import com.pandulapeter.campfire.chordpro.edit.ChordProMetadataFields.Field
import com.pandulapeter.campfire.chordpro.model.displayTitle
import com.pandulapeter.campfire.data.model.song.Song
import com.pandulapeter.campfire.data.repository.api.SongRepository
import com.pandulapeter.campfire.domain.api.songs.CreateSongUseCase
import org.koin.core.annotation.Factory

@Factory
internal class CreateSongUseCaseImpl(
    private val songRepository: SongRepository,
) : CreateSongUseCase {

    override suspend operator fun invoke(
        title: String,
        artist: String,
        metadata: Map<Field, String>,
    ): Song {
        val written = ChordProMetadataFields.set(text = newSongTemplate(title, artist), values = metadata.filterKeys { it != Field.TITLE && it != Field.ARTIST })
        // Named by the title as the library shows it, the subtitle included, as an import of the same text would be:
        // a file named by the title alone would be offered Update file name the moment it was created.
        return songRepository.createSong(
            title = ChordProParser.parseMetadata(written).displayTitle(fallback = title.trim()),
            artist = artist.trim(),
            text = written,
        )
    }
}
