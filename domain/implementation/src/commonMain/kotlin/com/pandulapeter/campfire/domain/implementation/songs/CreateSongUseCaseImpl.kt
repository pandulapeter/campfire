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
import com.pandulapeter.campfire.chordpro.edit.ChordProPrettifier
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
        text: String?,
    ): Song {
        val written = if (text == null) {
            ChordProMetadataFields.set(text = template(title, artist), values = metadata.filterKeys { it != Field.TITLE && it != Field.ARTIST })
        } else {
            // What the form says replaces what the text said, a cleared field included, since a cleared field is a wrong
            // guess taken back. Then the text gets the template's header lines it has none of, so that a pasted song
            // opens in the editor shaped like any new one, with the same lines to fill in - and what it declares, in its
            // header or as a change in its body, is left as it is. Formatted again once its header is whole, which sets
            // the header apart from the song as an import's formatting does.
            ChordProPrettifier.prettify(
                ChordProMetadataFields.declare(
                    text = ChordProMetadataFields.set(
                        text = text,
                        values = metadata.filterKeys { it != Field.TITLE && it != Field.ARTIST } + mapOf(Field.TITLE to title, Field.ARTIST to artist),
                    ),
                    values = TEMPLATE_VALUES,
                ),
            )
        }
        // Named by the title as the library shows it, the subtitle included, as an import of the same text would be:
        // a file named by the title alone would be offered Update file name the moment it was created.
        return songRepository.createSong(
            title = ChordProParser.parseMetadata(written).displayTitle(fallback = title.trim()),
            artist = artist.trim(),
            text = written,
        )
    }

    /**
     * A new song with no text of its own holds what the user typed, a line for each of the four values the song is
     * played by and the skeleton of a first verse, so that the editor opens on something that is already shaped like a
     * song rather than on an empty page. The capo, the tempo and the time signature start on the values the app plays a
     * song by when it declares none (no capo, the metronome's 120 and 4/4), so writing them changes nothing until one is
     * edited; the key has no such default and starts empty, as does the artist where none was given, to be filled in.
     */
    private fun template(title: String, artist: String) = buildString {
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
    }
}

/** The lines of [CreateSongUseCaseImpl]'s template a pasted song is given where it has none of its own. */
private val TEMPLATE_VALUES = linkedMapOf(
    Field.ARTIST to "",
    Field.KEY to "",
    Field.CAPO to "0",
    Field.TEMPO to "120",
    Field.TIME to "4/4",
)
