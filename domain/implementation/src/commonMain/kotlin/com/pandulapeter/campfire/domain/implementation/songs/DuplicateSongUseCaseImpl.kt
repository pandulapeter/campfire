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
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.repository.api.SongContentRepository
import com.pandulapeter.campfire.data.repository.api.SongRepository
import com.pandulapeter.campfire.domain.api.songs.DuplicateSongUseCase
import org.koin.core.annotation.Factory

@Factory
internal class DuplicateSongUseCaseImpl(
    private val songRepository: SongRepository,
    private val songContentRepository: SongContentRepository,
) : DuplicateSongUseCase {

    /**
     * The copy is made of the file as it is when the user confirms rather than as it was when the sheet opened, so that
     * an edit synced in meanwhile is not lost from it; only the fields the user changed are written over it. Named as
     * [CreateSongUseCaseImpl] names a new song, by the title as the library shows it, the subtitle included: a "Live"
     * or "Acoustic" copy told apart by its subtitle alone gets a file name of its own rather than the original's with
     * a number after it.
     */
    override suspend operator fun invoke(fileName: String, changes: Map<Field, String>): Song? {
        val original = songContentRepository.loadSongContent(fileName) ?: return null
        val text = ChordProMetadataFields.set(text = original.text, values = changes)
        val metadata = ChordProParser.parseMetadata(text)
        return songRepository.createSong(
            title = metadata.displayTitle(fallback = fileName.substringBeforeLast('.')),
            artist = metadata.artist.orEmpty().trim(),
            text = text,
        )
    }
}
