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
import com.pandulapeter.campfire.chordpro.edit.ChordProPrettifier
import com.pandulapeter.campfire.domain.api.songs.DraftSongFromTextUseCase
import org.koin.core.annotation.Factory

@Factory
internal class DraftSongFromTextUseCaseImpl : DraftSongFromTextUseCase {

    override operator fun invoke(
        text: String,
        placeholderTitle: String,
        placeholderArtist: String,
        suggestedTitle: String?,
        suggestedArtist: String?,
    ): String {
        val metadata = ChordProParser.parseMetadata(text)
        // Only a field the text says nothing about gets its suggestion or its placeholder, so a title or an artist it declares is kept as
        // written. Then the template's header lines it has none of, so that the song opens in the editor shaped like any
        // new one, with the same lines to fill in, and formatted once its header is whole, which sets the header apart
        // from the song as an import's formatting does.
        val placeholders = buildMap {
            if (metadata.title.isNullOrBlank()) put(Field.TITLE, suggestedTitle?.ifBlank { null } ?: placeholderTitle)
            if (metadata.artist.isNullOrBlank()) put(Field.ARTIST, suggestedArtist?.ifBlank { null } ?: placeholderArtist)
        }
        return ChordProPrettifier.prettify(
            ChordProMetadataFields.declare(
                text = ChordProMetadataFields.set(text = text, values = placeholders),
                values = NEW_SONG_TEMPLATE_VALUES,
            ),
        )
    }
}
