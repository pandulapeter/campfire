/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.implementation.useCases

import com.pandulapeter.campfire.chordpro.ChordProMetadataFields
import com.pandulapeter.campfire.chordpro.ChordProMetadataFields.Field
import com.pandulapeter.campfire.chordpro.ChordProParser
import com.pandulapeter.campfire.chordpro.model.displayTitle
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.repository.api.SongRepository
import com.pandulapeter.campfire.domain.api.useCases.CreateSongUseCase
import org.koin.core.annotation.Factory

@Factory
class CreateSongUseCaseImpl internal constructor(
    private val songRepository: SongRepository,
) : CreateSongUseCase {

    /**
     * The new file holds what the user typed, a line for each of the four values the song is played by and the
     * skeleton of a first verse, so that the editor opens on something that is already shaped like a song rather than
     * on an empty page. The capo, the tempo and the time signature start on the values the app plays a song by when it
     * declares none (no capo, the metronome's 120 and 4/4), so writing them changes nothing until one is edited; the
     * key has no such default and starts empty, as does the artist where none was given, to be filled in.
     */
    override suspend operator fun invoke(title: String, artist: String, metadata: Map<Field, String>): Song {
        val text = ChordProMetadataFields.set(
            text = buildString {
                append("{title: ").append(title.trim()).append("}\n")
                append("{artist: ").append(artist.trim()).append("}\n")
                append("{key: }\n")
                append("{capo: 0}\n")
                append("{tempo: 120}\n")
                append("{time: 4/4}\n")
                append("\n")
                append("{start_of_verse}\n")
                // The blank line the editor puts the caret on.
                append("\n")
                append("{end_of_verse}\n")
            },
            // Title and artist also determine the file name, so those arguments remain authoritative.
            values = metadata.filterKeys { it != Field.TITLE && it != Field.ARTIST },
        )
        // Named by the title as the library shows it, the subtitle included, as an import of the same text would be:
        // a file named by the title alone would be offered Update file name the moment it was created.
        return songRepository.createSong(
            title = ChordProParser.parseMetadata(text).displayTitle(fallback = title.trim()),
            artist = artist.trim(),
            text = text,
        )
    }
}
